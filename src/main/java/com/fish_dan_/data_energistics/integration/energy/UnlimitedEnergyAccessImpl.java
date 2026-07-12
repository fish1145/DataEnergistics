package com.fish_dan_.data_energistics.integration.energy;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.mixin.core.NeoForgeEnergyStorageAccessor;

import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verified {@link UnlimitedEnergyAccess} implementation backed by a NeoForge accessor and cached method handles.
 *
 * <p>
 * Every direct mutation starts from a state that agrees with the public capability, verifies the resulting state, and
 * rolls back on invocation failure, invalid return values, or inconsistent read-back. Reflection is isolated to the
 * one-time construction of cached {@link MethodHandle} and {@link VarHandle} access plans.
 */
public final class UnlimitedEnergyAccessImpl implements UnlimitedEnergyAccess {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final List<String> ENERGY_FIELD_NAMES = List.of("energy", "storedEnergy", "energyStored", "stored",
            "amount");
    private static final List<String> CAPACITY_FIELD_NAMES = List.of("capacity", "maxEnergy", "maxEnergyStored",
            "maxStored", "maxStorage");
    private static final List<String> WRAPPER_FIELD_NAMES = List.of("container", "storage", "delegate", "wrapped",
            "backingStorage");
    private static final List<String> NOTIFICATION_METHOD_NAMES = List.of("update", "onEnergyChanged",
            "onContentsChanged");
    private static final Map<Class<?>, ReflectionPlan> REFLECTION_PLANS = new ConcurrentHashMap<>();
    private static final AmountWriter TYPED_AMOUNT_WRITER = new AmountWriter() {

        @Override
        public boolean write(Object target, long amount) {
            if (!supports(amount)) {
                return false;
            }
            try {
                ((UnlimitedEnergyStorage) target).setStoredEnergyLong(amount);
                return true;
            } catch (RuntimeException | LinkageError exception) {
                Data_Energistics.LOGGER.error("Could not write typed unlimited energy storage {}",
                        target.getClass().getName(), exception);
                return false;
            }
        }

        @Override
        public long maxValue() {
            return Long.MAX_VALUE;
        }
    };
    private static final StateAccess TYPED_STATE_ACCESS = new StateAccess(
            UnlimitedEnergyAccessImpl::readTypedStored,
            UnlimitedEnergyAccessImpl::readTypedCapacity,
            TYPED_AMOUNT_WRITER,
            Long.MAX_VALUE);
    private static final DirectAccess TYPED_DIRECT_ACCESS = new DirectAccess(TYPED_STATE_ACCESS, null, null);
    private static final AmountWriter NEOFORGE_AMOUNT_WRITER = new AmountWriter() {

        @Override
        public boolean write(Object target, long amount) {
            if (!supports(amount)) {
                return false;
            }
            ((NeoForgeEnergyStorageAccessor) target).dataEnergistics$setEnergy((int) amount);
            return true;
        }

        @Override
        public long maxValue() {
            return Integer.MAX_VALUE;
        }
    };
    private static final StateAccess NEOFORGE_STATE_ACCESS = new StateAccess(
            target -> (long) ((NeoForgeEnergyStorageAccessor) target).dataEnergistics$getEnergy(),
            target -> (long) ((NeoForgeEnergyStorageAccessor) target).dataEnergistics$getCapacity(),
            NEOFORGE_AMOUNT_WRITER,
            Integer.MAX_VALUE);
    private static final DirectAccess NEOFORGE_DIRECT_ACCESS = new DirectAccess(NEOFORGE_STATE_ACCESS, null, null);

    @Override
    public long stored(IEnergyStorage storage) {
        Objects.requireNonNull(storage, "storage");
        Optional<DirectTarget> target = findDirectTarget(storage);
        if (target.isPresent()) {
            Snapshot snapshot = readVerifiedSnapshot(storage, target.get());
            if (snapshot != null) {
                return snapshot.stored();
            }
        }
        Integer stored = readCapabilityValue(storage, true);
        return stored == null ? 0L : stored;
    }

    @Override
    public long capacity(IEnergyStorage storage) {
        Objects.requireNonNull(storage, "storage");
        Optional<DirectTarget> target = findDirectTarget(storage);
        if (target.isPresent()) {
            Snapshot snapshot = readVerifiedSnapshot(storage, target.get());
            if (snapshot != null) {
                return snapshot.capacity();
            }
        }
        Integer capacity = readCapabilityValue(storage, false);
        return capacity == null ? 0L : capacity;
    }

    @Override
    public boolean canReceive(IEnergyStorage storage) {
        return readCapabilityPermission(Objects.requireNonNull(storage, "storage"), true);
    }

    @Override
    public boolean canExtract(IEnergyStorage storage) {
        return readCapabilityPermission(Objects.requireNonNull(storage, "storage"), false);
    }

    @Override
    public long insert(IEnergyStorage storage, long amount, boolean simulate) {
        Objects.requireNonNull(storage, "storage");
        validateRequestedAmount(amount);
        if (amount == 0L || !canReceive(storage)) {
            return 0L;
        }
        return transfer(storage, amount, simulate, true);
    }

    @Override
    public long extract(IEnergyStorage storage, long amount, boolean simulate) {
        Objects.requireNonNull(storage, "storage");
        validateRequestedAmount(amount);
        if (amount == 0L || !canExtract(storage)) {
            return 0L;
        }
        return transfer(storage, amount, simulate, false);
    }

    @Override
    public long rollbackExtraction(IEnergyStorage storage, long amount) {
        Objects.requireNonNull(storage, "storage");
        validateRequestedAmount(amount);
        if (amount == 0L) {
            return 0L;
        }

        Optional<DirectTarget> resolvedTarget = findDirectTarget(storage);
        if (resolvedTarget.isEmpty()) {
            return UNAVAILABLE;
        }

        DirectTarget target = resolvedTarget.get();
        Snapshot before = readVerifiedSnapshot(storage, target);
        if (before == null || !target.access().state().writer().supports(before.stored())) {
            return UNAVAILABLE;
        }

        long restoredAmount = safeAdd(before.stored(), amount);
        if (restoredAmount < 0L || restoredAmount > before.capacity() || !target.access().state().writer().supports(restoredAmount)) {
            Data_Energistics.LOGGER.error(
                    "Could not compensate {} FE on unlimited energy source {} with state {}/{}",
                    amount,
                    target.target().getClass().getName(),
                    before.stored(),
                    before.capacity());
            return UNAVAILABLE;
        }
        if (!target.access().state().writer().write(target.target(), restoredAmount)) {
            return UNAVAILABLE;
        }

        Snapshot after = readVerifiedSnapshot(storage, target);
        if (after == null || after.stored() != restoredAmount || after.capacity() != before.capacity()) {
            Data_Energistics.LOGGER.error(
                    "Unlimited extraction compensation on {} failed read-back verification",
                    target.target().getClass().getName());
            rollback(storage, target, before);
            return UNAVAILABLE;
        }
        return amount;
    }

    @Override
    public void notifyStorageChanged(IEnergyStorage storage) {
        Objects.requireNonNull(storage, "storage");
        Set<Object> notifiedTargets = Collections.newSetFromMap(new IdentityHashMap<>());
        findDirectTarget(storage).ifPresent(target -> {
            if (target.target() instanceof UnlimitedEnergyStorage typedStorage) {
                notifyTypedStorage(typedStorage, notifiedTargets);
            } else {
                notifyTarget(target.target(), notifiedTargets);
            }
        });
        notifyTarget(storage, notifiedTargets);
    }

    private static long transfer(IEnergyStorage storage, long amount, boolean simulate, boolean inserting) {
        Optional<DirectTarget> resolvedTarget = findDirectTarget(storage);
        if (resolvedTarget.isEmpty()) {
            return UNAVAILABLE;
        }

        DirectTarget target = resolvedTarget.get();
        Snapshot before = readVerifiedSnapshot(storage, target);
        if (before == null || !target.access().state().writer().supports(before.stored())) {
            return UNAVAILABLE;
        }

        long available = inserting ? insertionSpace(before, target.access().state()) : before.stored();
        if (available <= 0L) {
            return 0L;
        }

        long requested = Math.min(amount, available);
        AmountOperation operation = inserting ? target.access().insertOperation() : target.access().extractOperation();
        if (operation != null) {
            return invokeOperation(storage, target, before, requested, simulate, inserting, operation);
        }
        return writeAmountDirectly(storage, target, before, requested, simulate, inserting);
    }

    private static long invokeOperation(IEnergyStorage storage, DirectTarget target, Snapshot before, long requested,
                                        boolean simulate, boolean inserting, AmountOperation operation) {
        long invocationAmount = Math.min(requested, operation.maxArgument());
        Long changed = operation.invoke(target.target(), invocationAmount, simulate);
        if (changed == null || changed < 0L || changed > invocationAmount || changed > requested) {
            Data_Energistics.LOGGER.error("Unlimited energy operation on {} returned invalid amount {} for request {}",
                    target.target().getClass().getName(), changed, invocationAmount);
            rollback(storage, target, before);
            return UNAVAILABLE;
        }

        long expectedStored = inserting ? safeAdd(before.stored(), changed) : before.stored() - changed;
        if (expectedStored < 0L || expectedStored > before.capacity()) {
            Data_Energistics.LOGGER.error("Unlimited energy operation on {} produced out-of-range state {}",
                    target.target().getClass().getName(), expectedStored);
            rollback(storage, target, before);
            return UNAVAILABLE;
        }

        Snapshot after = readVerifiedSnapshot(storage, target);
        long requiredStored = simulate ? before.stored() : expectedStored;
        if (after == null || after.stored() != requiredStored || after.capacity() != before.capacity()) {
            Data_Energistics.LOGGER.error("Unlimited energy operation on {} did not produce its reported state",
                    target.target().getClass().getName());
            rollback(storage, target, before);
            return UNAVAILABLE;
        }
        return changed;
    }

    private static long writeAmountDirectly(IEnergyStorage storage, DirectTarget target, Snapshot before, long requested,
                                            boolean simulate, boolean inserting) {
        long targetAmount = inserting ? safeAdd(before.stored(), requested) : before.stored() - requested;
        if (targetAmount < 0L || targetAmount > before.capacity() || !target.access().state().writer().supports(targetAmount)) {
            return UNAVAILABLE;
        }
        if (simulate) {
            return requested;
        }

        if (!target.access().state().writer().write(target.target(), targetAmount)) {
            rollback(storage, target, before);
            return UNAVAILABLE;
        }

        Snapshot after = readVerifiedSnapshot(storage, target);
        if (after == null || after.stored() != targetAmount || after.capacity() != before.capacity()) {
            Data_Energistics.LOGGER.error("Unlimited direct write on {} failed read-back verification",
                    target.target().getClass().getName());
            rollback(storage, target, before);
            return UNAVAILABLE;
        }
        return requested;
    }

    private static void rollback(IEnergyStorage storage, DirectTarget target, Snapshot before) {
        boolean restored = target.access().state().writer().write(target.target(), before.stored());
        Snapshot afterRollback = restored ? readVerifiedSnapshot(storage, target) : null;
        if (afterRollback == null || afterRollback.stored() != before.stored() || afterRollback.capacity() != before.capacity()) {
            Data_Energistics.LOGGER.error("Could not roll back unlimited energy mutation on {}",
                    target.target().getClass().getName());
        }
    }

    private static long insertionSpace(Snapshot snapshot, StateAccess state) {
        long writableCapacity = Math.min(snapshot.capacity(), state.maxWritable());
        return writableCapacity <= snapshot.stored() ? 0L : writableCapacity - snapshot.stored();
    }

    @Nullable
    private static Snapshot readVerifiedSnapshot(IEnergyStorage storage, DirectTarget target) {
        Long stored = target.access().state().stored().read(target.target());
        if (stored == null || stored < 0L) {
            return null;
        }

        AmountReader capacityReader = target.access().state().capacity();
        Long directCapacity = capacityReader == null ? null : capacityReader.read(target.target());
        if (capacityReader != null && directCapacity == null) {
            return null;
        }

        Integer reportedStored = readCapabilityValue(storage, true);
        Integer reportedCapacity = readCapabilityValue(storage, false);
        if (reportedStored == null || reportedCapacity == null) {
            return null;
        }

        long capacity = directCapacity == null ? reportedCapacity : directCapacity;
        if (capacity < stored || !matchesCapabilityValue(stored, reportedStored) || directCapacity != null && !matchesCapabilityValue(directCapacity, reportedCapacity)) {
            Data_Energistics.LOGGER.debug(
                    "Rejected inconsistent unlimited energy state on {}: direct={}/{}, reported={}/{}",
                    target.target().getClass().getName(), stored, capacity, reportedStored, reportedCapacity);
            return null;
        }
        return new Snapshot(stored, capacity);
    }

    private static Optional<DirectTarget> findDirectTarget(IEnergyStorage storage) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return findDirectTarget(storage, visited);
    }

    private static Optional<DirectTarget> findDirectTarget(Object candidate, Set<Object> visited) {
        if (candidate == null || !visited.add(candidate)) {
            return Optional.empty();
        }
        if (candidate instanceof UnlimitedEnergyStorage) {
            return Optional.of(new DirectTarget(candidate, TYPED_DIRECT_ACCESS));
        }
        if (candidate instanceof NeoForgeEnergyStorageAccessor) {
            return Optional.of(new DirectTarget(candidate, NEOFORGE_DIRECT_ACCESS));
        }

        ReflectionPlan plan = reflectionPlan(candidate.getClass());
        for (ObjectReader wrapper : plan.wrappers()) {
            Object wrapped = wrapper.read(candidate);
            Optional<DirectTarget> nested = findDirectTarget(wrapped, visited);
            if (nested.isPresent()) {
                return nested;
            }
        }
        return plan.directAccess().map(access -> new DirectTarget(candidate, access));
    }

    private static ReflectionPlan reflectionPlan(Class<?> type) {
        return REFLECTION_PLANS.computeIfAbsent(type, UnlimitedEnergyAccessImpl::resolveReflectionPlan);
    }

    private static ReflectionPlan resolveReflectionPlan(Class<?> type) {
        List<ObjectReader> wrappers = findObjectFields(type, WRAPPER_FIELD_NAMES);
        List<VoidOperation> notifications = new ArrayList<>();
        for (String methodName : NOTIFICATION_METHOD_NAMES) {
            findNoArgVoidMethod(type, methodName).ifPresent(notifications::add);
        }

        Optional<AmountReader> methodStored = findNoArgNumericMethod(type, "getAmount");
        Optional<AmountReader> methodCapacity = findNoArgNumericMethod(type, "getCapacity");
        Optional<AmountWriter> methodWriter = findSingleNumericVoidMethod(type, "setAmount");
        Optional<NumericField> storedField = findNumericField(type, ENERGY_FIELD_NAMES, true);
        Optional<NumericField> capacityField = findNumericField(type, CAPACITY_FIELD_NAMES, false);

        StateAccess state = null;
        if (methodStored.isPresent() && methodWriter.isPresent()) {
            AmountReader capacity = methodCapacity.orElseGet(() -> capacityField.map(NumericField::reader).orElse(null));
            state = new StateAccess(methodStored.get(), capacity, methodWriter.get(), methodWriter.get().maxValue());
        } else if (storedField.isPresent()) {
            AmountReader capacity = capacityField.map(NumericField::reader).orElseGet(() -> methodCapacity.orElse(null));
            state = new StateAccess(storedField.get().reader(), capacity, storedField.get().writer(),
                    storedField.get().writer().maxValue());
        }

        Optional<DirectAccess> directAccess = Optional.empty();
        if (state != null) {
            AmountOperation insert = findAmountOperation(type, "insertIgnoringLimit").orElse(null);
            AmountOperation extract = findAmountOperation(type, "extractIgnoringLimit").orElse(null);
            directAccess = Optional.of(new DirectAccess(state, insert, extract));
        }
        return new ReflectionPlan(List.copyOf(wrappers), directAccess, List.copyOf(notifications));
    }

    private static Optional<NumericField> findNumericField(Class<?> owner, List<String> names, boolean writable) {
        for (String name : names) {
            Class<?> type = owner;
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(name);
                    int modifiers = field.getModifiers();
                    if (Modifier.isStatic(modifiers) || !isNumericType(field.getType()) || writable && Modifier.isFinal(modifiers)) {
                        Data_Energistics.LOGGER.debug("Ignored unsupported unlimited energy field {}#{}", type.getName(),
                                name);
                        break;
                    }
                    VarHandle handle = MethodHandles.privateLookupIn(type, LOOKUP).unreflectVarHandle(field);
                    FieldAmountAccess access = new FieldAmountAccess(handle, type.getName() + "#" + name);
                    return Optional.of(new NumericField(access, access));
                } catch (NoSuchFieldException exception) {
                    type = type.getSuperclass();
                } catch (IllegalAccessException | RuntimeException | LinkageError exception) {
                    Data_Energistics.LOGGER.debug("Could not resolve unlimited energy field {}#{}", type.getName(), name,
                            exception);
                    break;
                }
            }
        }
        return Optional.empty();
    }

    private static List<ObjectReader> findObjectFields(Class<?> owner, List<String> names) {
        List<ObjectReader> fields = new ArrayList<>();
        for (String name : names) {
            Class<?> type = owner;
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(name);
                    if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                        Data_Energistics.LOGGER.debug("Ignored unsupported unlimited energy wrapper field {}#{}",
                                type.getName(), name);
                        break;
                    }
                    VarHandle handle = MethodHandles.privateLookupIn(type, LOOKUP).unreflectVarHandle(field);
                    fields.add(new FieldObjectReader(handle, type.getName() + "#" + name));
                    break;
                } catch (NoSuchFieldException exception) {
                    type = type.getSuperclass();
                } catch (IllegalAccessException | RuntimeException | LinkageError exception) {
                    Data_Energistics.LOGGER.debug("Could not resolve unlimited energy wrapper field {}#{}", type.getName(),
                            name, exception);
                    break;
                }
            }
        }
        return fields;
    }

    private static Optional<AmountReader> findNoArgNumericMethod(Class<?> owner, String name) {
        Method method = findCompatibleMethod(owner, name, candidate -> candidate.getParameterCount() == 0 && isNumericType(candidate.getReturnType()));
        return method == null ? Optional.empty() : createMethodHandle(method).map(handle -> new MethodAmountReader(handle, describe(method)));
    }

    private static Optional<AmountWriter> findSingleNumericVoidMethod(Class<?> owner, String name) {
        Method method = findCompatibleMethod(owner, name, candidate -> candidate.getParameterCount() == 1 && isNumericType(candidate.getParameterTypes()[0]) && candidate.getReturnType() == void.class);
        return method == null ? Optional.empty() : createMethodHandle(method)
                .map(handle -> new MethodAmountWriter(handle, candidateMaxValue(method.getParameterTypes()[0]),
                        describe(method)));
    }

    private static Optional<AmountOperation> findAmountOperation(Class<?> owner, String name) {
        Method method = findCompatibleMethod(owner, name,
                candidate -> candidate.getParameterCount() == 2 && isNumericType(candidate.getParameterTypes()[0]) && candidate.getParameterTypes()[1] == boolean.class && isNumericType(candidate.getReturnType()));
        return method == null ? Optional.empty() : createMethodHandle(method)
                .map(handle -> new MethodAmountOperation(handle, candidateMaxValue(method.getParameterTypes()[0]),
                        describe(method)));
    }

    private static Optional<VoidOperation> findNoArgVoidMethod(Class<?> owner, String name) {
        Method method = findCompatibleMethod(owner, name,
                candidate -> candidate.getParameterCount() == 0 && candidate.getReturnType() == void.class);
        return method == null ? Optional.empty() : createMethodHandle(method).map(handle -> new MethodVoidOperation(handle, describe(method)));
    }

    @Nullable
    private static Method findCompatibleMethod(Class<?> owner, String name, MethodPredicate predicate) {
        Class<?> type = owner;
        while (type != null) {
            boolean namedMethodFound = false;
            try {
                for (Method method : type.getDeclaredMethods()) {
                    if (!method.getName().equals(name)) {
                        continue;
                    }
                    namedMethodFound = true;
                    if (!Modifier.isStatic(method.getModifiers()) && predicate.test(method)) {
                        return method;
                    }
                }
            } catch (RuntimeException | LinkageError exception) {
                Data_Energistics.LOGGER.debug("Could not inspect unlimited energy method {}#{}", type.getName(), name,
                        exception);
                return null;
            }
            if (namedMethodFound) {
                Data_Energistics.LOGGER.debug("Ignored unsupported unlimited energy method {}#{}", type.getName(), name);
                return null;
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Optional<MethodHandle> createMethodHandle(Method method) {
        try {
            return Optional.of(MethodHandles.privateLookupIn(method.getDeclaringClass(), LOOKUP).unreflect(method));
        } catch (IllegalAccessException | RuntimeException | LinkageError exception) {
            Data_Energistics.LOGGER.debug("Could not resolve unlimited energy method {}", describe(method), exception);
            return Optional.empty();
        }
    }

    private static void notifyTarget(Object target, Set<Object> notifiedTargets) {
        if (!notifiedTargets.add(target)) {
            return;
        }
        for (VoidOperation notification : reflectionPlan(target.getClass()).notifications()) {
            notification.invoke(target);
        }
    }

    @Nullable
    private static Long readTypedStored(Object target) {
        try {
            return ((UnlimitedEnergyStorage) target).getStoredEnergyLong();
        } catch (RuntimeException | LinkageError exception) {
            Data_Energistics.LOGGER.error("Could not read typed unlimited stored energy from {}",
                    target.getClass().getName(), exception);
            return null;
        }
    }

    @Nullable
    private static Long readTypedCapacity(Object target) {
        try {
            return ((UnlimitedEnergyStorage) target).getEnergyCapacityLong();
        } catch (RuntimeException | LinkageError exception) {
            Data_Energistics.LOGGER.error("Could not read typed unlimited energy capacity from {}",
                    target.getClass().getName(), exception);
            return null;
        }
    }

    private static void notifyTypedStorage(UnlimitedEnergyStorage storage, Set<Object> notifiedTargets) {
        if (!notifiedTargets.add(storage)) {
            return;
        }
        try {
            storage.onUnlimitedEnergyChanged();
        } catch (RuntimeException | LinkageError exception) {
            Data_Energistics.LOGGER.error("Could not notify typed unlimited energy storage {}",
                    storage.getClass().getName(), exception);
        }
    }

    private static boolean matchesCapabilityValue(long directValue, int reportedValue) {
        return reportedValue == Math.min(directValue, Integer.MAX_VALUE);
    }

    @Nullable
    private static Integer readCapabilityValue(IEnergyStorage storage, boolean stored) {
        String description = stored ? "stored energy" : "energy capacity";
        try {
            int value = stored ? storage.getEnergyStored() : storage.getMaxEnergyStored();
            if (value >= 0) {
                return value;
            }
            Data_Energistics.LOGGER.error("Energy capability {} reported negative {}: {}",
                    storage.getClass().getName(), description, value);
        } catch (RuntimeException | LinkageError exception) {
            Data_Energistics.LOGGER.error("Energy capability {} failed to report {}",
                    storage.getClass().getName(), description, exception);
        }
        return null;
    }

    private static boolean readCapabilityPermission(IEnergyStorage storage, boolean receive) {
        String description = receive ? "receive" : "extract";
        try {
            return receive ? storage.canReceive() : storage.canExtract();
        } catch (RuntimeException | LinkageError exception) {
            Data_Energistics.LOGGER.error("Energy capability {} failed to report {} permission",
                    storage.getClass().getName(), description, exception);
            return false;
        }
    }

    private static long safeAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MIN_VALUE : left + right;
    }

    private static void validateRequestedAmount(long amount) {
        if (amount < 0L) {
            throw new IllegalArgumentException("Energy amount must not be negative: " + amount);
        }
    }

    private static boolean isNumericType(Class<?> type) {
        return type == int.class || type == long.class;
    }

    private static long candidateMaxValue(Class<?> type) {
        return type == int.class ? Integer.MAX_VALUE : Long.MAX_VALUE;
    }

    private static String describe(Method method) {
        return method.getDeclaringClass().getName() + "#" + method.getName();
    }

    private interface MethodPredicate {

        boolean test(Method method);
    }

    private interface AmountReader {

        @Nullable
        Long read(Object target);
    }

    private interface AmountWriter {

        boolean write(Object target, long amount);

        long maxValue();

        default boolean supports(long amount) {
            return amount >= 0L && amount <= maxValue();
        }
    }

    private interface AmountOperation {

        @Nullable
        Long invoke(Object target, long amount, boolean simulate);

        long maxArgument();
    }

    private interface ObjectReader {

        @Nullable
        Object read(Object target);
    }

    private interface VoidOperation {

        void invoke(Object target);
    }

    private record StateAccess(AmountReader stored, @Nullable AmountReader capacity, AmountWriter writer,
                               long maxWritable) {}

    private record DirectAccess(StateAccess state, @Nullable AmountOperation insertOperation,
                                @Nullable AmountOperation extractOperation) {}

    private record DirectTarget(Object target, DirectAccess access) {}

    private record Snapshot(long stored, long capacity) {}

    private record ReflectionPlan(List<ObjectReader> wrappers, Optional<DirectAccess> directAccess,
                                  List<VoidOperation> notifications) {}

    private record NumericField(AmountReader reader, AmountWriter writer) {}

    private record FieldAmountAccess(VarHandle handle, String description) implements AmountReader, AmountWriter {

        @Override
        @Nullable
        public Long read(Object target) {
            try {
                Object value = this.handle.get(target);
                return value instanceof Number number ? number.longValue() : null;
            } catch (RuntimeException | LinkageError exception) {
                Data_Energistics.LOGGER.error("Could not read unlimited energy field {}", this.description, exception);
                return null;
            }
        }

        @Override
        public boolean write(Object target, long amount) {
            if (!supports(amount)) {
                return false;
            }
            try {
                if (this.handle.varType() == int.class) {
                    this.handle.set(target, (int) amount);
                } else {
                    this.handle.set(target, amount);
                }
                return true;
            } catch (RuntimeException | LinkageError exception) {
                Data_Energistics.LOGGER.error("Could not write unlimited energy field {}", this.description, exception);
                return false;
            }
        }

        @Override
        public long maxValue() {
            return this.handle.varType() == int.class ? Integer.MAX_VALUE : Long.MAX_VALUE;
        }
    }

    private record FieldObjectReader(VarHandle handle, String description) implements ObjectReader {

        @Override
        @Nullable
        public Object read(Object target) {
            try {
                return this.handle.get(target);
            } catch (RuntimeException | LinkageError exception) {
                Data_Energistics.LOGGER.error("Could not read unlimited energy wrapper field {}", this.description,
                        exception);
                return null;
            }
        }
    }

    private record MethodAmountReader(MethodHandle handle, String description) implements AmountReader {

        @Override
        @Nullable
        public Long read(Object target) {
            try {
                Object result = this.handle.invokeWithArguments(target);
                return result instanceof Number number ? number.longValue() : null;
            } catch (Throwable throwable) {
                Data_Energistics.LOGGER.error("Could not invoke unlimited energy reader {}", this.description, throwable);
                return null;
            }
        }
    }

    private record MethodAmountWriter(MethodHandle handle, long maxValue, String description) implements AmountWriter {

        @Override
        public boolean write(Object target, long amount) {
            if (!supports(amount)) {
                return false;
            }
            try {
                Object argument;
                if (this.maxValue == Integer.MAX_VALUE) {
                    argument = Integer.valueOf((int) amount);
                } else {
                    argument = Long.valueOf(amount);
                }
                this.handle.invokeWithArguments(target, argument);
                return true;
            } catch (Throwable throwable) {
                Data_Energistics.LOGGER.error("Could not invoke unlimited energy writer {}", this.description, throwable);
                return false;
            }
        }
    }

    private record MethodAmountOperation(MethodHandle handle, long maxArgument,
                                         String description)
            implements AmountOperation {

        @Override
        @Nullable
        public Long invoke(Object target, long amount, boolean simulate) {
            try {
                Object argument;
                if (this.maxArgument == Integer.MAX_VALUE) {
                    argument = Integer.valueOf((int) amount);
                } else {
                    argument = Long.valueOf(amount);
                }
                Object result = this.handle.invokeWithArguments(target, argument, simulate);
                return result instanceof Number number ? number.longValue() : null;
            } catch (Throwable throwable) {
                Data_Energistics.LOGGER.error("Could not invoke unlimited energy operation {}", this.description,
                        throwable);
                return null;
            }
        }
    }

    private record MethodVoidOperation(MethodHandle handle, String description) implements VoidOperation {

        @Override
        public void invoke(Object target) {
            try {
                this.handle.invokeWithArguments(target);
            } catch (Throwable throwable) {
                Data_Energistics.LOGGER.error("Could not invoke unlimited energy notification {}", this.description,
                        throwable);
            }
        }
    }
}
