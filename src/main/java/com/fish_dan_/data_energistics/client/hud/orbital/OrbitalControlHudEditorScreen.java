package com.fish_dan_.data_energistics.client.hud.orbital;

import com.fish_dan_.data_energistics.client.hud.orbital.rendering.OrbitalHudRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Native client screen used to place, scale and tune the orbital HUD. */
public final class OrbitalControlHudEditorScreen extends Screen {

    private final Runnable onClose;
    private OrbitalControlHudPreferences.Preferences working;
    private Button anchorButton;
    private Button modeButton;
    private HudSlider scaleSlider;
    private HudSlider opacitySlider;
    private boolean dragging;
    private boolean saveFailed;
    private boolean callbackInvoked;
    private double dragX;
    private double dragY;

    private OrbitalControlHudEditorScreen(Runnable onClose) {
        super(Component.translatable("screen.data_energistics.orbital_control_hud.editor"));
        this.onClose = onClose;
        this.working = OrbitalControlHudPreferences.current();
    }

    public static Screen create(Runnable onClose) {
        return new OrbitalControlHudEditorScreen(onClose);
    }

    public static void open(Runnable onClose) {
        Minecraft.getInstance().setScreen(create(onClose));
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 120;
        int top = this.height / 2 - 90;
        this.anchorButton = addRenderableWidget(Button.builder(anchorText(), button -> cycleAnchor()).bounds(left, top + 82, 112, 20).build());
        this.modeButton = addRenderableWidget(Button.builder(modeText(), button -> cycleMode()).bounds(left + 116, top + 82, 112, 20).build());
        scaleSlider = addRenderableWidget(new HudSlider(left, top + 110, 228, 20, true, working.scale()));
        opacitySlider = addRenderableWidget(new HudSlider(left, top + 134, 228, 20, false, working.opacity()));
        addRenderableWidget(Button.builder(Component.translatable("screen.data_energistics.orbital_control_hud.save"), button -> saveAndClose()).bounds(left, top + 160, 72, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.data_energistics.orbital_control_hud.cancel"), button -> closeScreen()).bounds(left + 78, top + 160, 72, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.data_energistics.orbital_control_hud.defaults"), button -> defaults()).bounds(left + 156, top + 160, 72, 20).build());
    }

    private void cycleAnchor() {
        OrbitalControlHudPreferences.Anchor[] values = OrbitalControlHudPreferences.Anchor.values();
        working = new OrbitalControlHudPreferences.Preferences(values[(working.anchor().ordinal() + 1) % values.length], working.offsetX(), working.offsetY(), working.scale(), working.opacity(), working.mode());
        anchorButton.setMessage(anchorText());
    }

    private void cycleMode() {
        OrbitalControlHudPreferences.DisplayMode[] values = OrbitalControlHudPreferences.DisplayMode.values();
        working = new OrbitalControlHudPreferences.Preferences(working.anchor(), working.offsetX(), working.offsetY(), working.scale(), working.opacity(), values[(working.mode().ordinal() + 1) % values.length]);
        modeButton.setMessage(modeText());
    }

    private Component anchorText() {
        return Component.translatable("screen.data_energistics.orbital_control_hud.anchor", Component.translatable("screen.data_energistics.orbital_control_hud.anchor." + working.anchor().name().toLowerCase(Locale.ROOT)));
    }

    private Component modeText() {
        return Component.translatable("screen.data_energistics.orbital_control_hud.mode", Component.translatable("screen.data_energistics.orbital_control_hud.mode." + working.mode().name().toLowerCase(Locale.ROOT)));
    }

    private void defaults() {
        working = OrbitalControlHudPreferences.defaults();
        anchorButton.setMessage(anchorText());
        modeButton.setMessage(modeText());
        scaleSlider.syncValue();
        opacitySlider.syncValue();
        scaleSlider.updateMessage();
        opacitySlider.updateMessage();
    }

    private void saveAndClose() {
        saveFailed = !OrbitalControlHudPreferences.save(working);
        if (!saveFailed) closeScreen();
    }

    private void closeScreen() {
        Minecraft.getInstance().setScreen(null);
        if (!callbackInvoked) {
            callbackInvoked = true;
            onClose.run();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int[] bounds = previewBounds();
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && mouseX >= bounds[0] && mouseY >= bounds[1] && mouseX < bounds[0] + bounds[2] && mouseY < bounds[1] + bounds[3]) {
            dragging = true;
            dragX = mouseX;
            dragY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            int dx = (int) Math.round(mouseX - this.dragX);
            int dy = (int) Math.round(mouseY - this.dragY);
            int[] bounds = previewBounds();
            int x = Math.clamp(bounds[0] + dx, 0, this.width - bounds[2]);
            int y = Math.clamp(bounds[1] + dy, 0, this.height - bounds[3]);
            boolean right = working.anchor() == OrbitalControlHudPreferences.Anchor.TOP_RIGHT || working.anchor() == OrbitalControlHudPreferences.Anchor.BOTTOM_RIGHT;
            boolean bottom = working.anchor() == OrbitalControlHudPreferences.Anchor.BOTTOM_LEFT || working.anchor() == OrbitalControlHudPreferences.Anchor.BOTTOM_RIGHT;
            working = new OrbitalControlHudPreferences.Preferences(working.anchor(), right ? this.width - bounds[2] - x : x,
                    bottom ? this.height - bounds[3] - y : y, working.scale(), working.opacity(), working.mode());
            this.dragX = mouseX;
            this.dragY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        closeScreen();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = this.width / 2 - 120;
        int top = this.height / 2 - 90;
        graphics.drawCenteredString(this.font, Component.translatable("screen.data_energistics.orbital_control_hud.editor"), this.width / 2, top - 20, 0xFFFFFFFF);
        graphics.drawString(this.font, Component.translatable("screen.data_energistics.orbital_control_hud.drag_hint"), left, top + 58, 0xFFB8C9D0);
        if (saveFailed) graphics.drawString(this.font, Component.translatable("screen.data_energistics.orbital_control_hud.save_failed"), left, top + 184, 0xFFFF6B6B);
        OrbitalHudRenderer.render(graphics, this.font, working, OrbitalControlHudClientState.snapshot().weapon(), this.width, this.height);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int[] previewBounds() {
        var bounds = OrbitalHudRenderer.placement(working, OrbitalControlHudClientState.snapshot().weapon(), this.width, this.height);
        return new int[] { bounds.x(), bounds.y(), bounds.width(), bounds.height() };
    }

    private final class HudSlider extends AbstractSliderButton {

        private final boolean scale;

        private HudSlider(int x, int y, int width, int height, boolean scale, float value) {
            super(x, y, width, height, Component.empty(), scale ? value - 0.75F : (value - 0.2F) / 0.8F);
            this.scale = scale;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(scale ? "screen.data_energistics.orbital_control_hud.scale" : "screen.data_energistics.orbital_control_hud.opacity", Math.round((scale ? working.scale() : working.opacity()) * 100)));
        }

        private void syncValue() {
            this.value = scale ? working.scale() - 0.75F : (working.opacity() - 0.2F) / 0.8F;
            updateMessage();
        }

        @Override
        protected void applyValue() {
            float value = (float) (scale ? 0.75F + this.value : 0.2F + this.value * 0.8F);
            working = new OrbitalControlHudPreferences.Preferences(working.anchor(), working.offsetX(), working.offsetY(), scale ? value : working.scale(), scale ? working.opacity() : value, working.mode());
            updateMessage();
        }
    }
}
