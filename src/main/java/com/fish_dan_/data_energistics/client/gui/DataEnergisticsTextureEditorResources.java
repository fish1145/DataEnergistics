package com.fish_dan_.data_energistics.client.gui;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;

import com.lowdragmc.lowdraglib2.editor.resource.BuiltinResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.EditorResourceEvent;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance;
import com.lowdragmc.lowdraglib2.editor.resource.TexturesResource;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;

/**
 * Registers the Data Energistics texture library in the LDLib2 UI Editor.
 *
 * <p>
 * The editor already knows how to configure every texture type used here. This registry only
 * gives the existing assets and compositions stable, selectable resource names.
 * </p>
 */
@OnlyIn(Dist.CLIENT)
public final class DataEnergisticsTextureEditorResources {

    private static final String TRINITY_TEXTURE_ROOT = Data_Energistics.MODID + ":textures/guis/trinity_data_core/";
    private static final String AUTO_BUILD_TEXTURE_ROOT = Data_Energistics.MODID + ":textures/guis/autobuild/";
    private static final String GUI_TEXTURE_ROOT = Data_Energistics.MODID + ":textures/guis/";

    private DataEnergisticsTextureEditorResources() {}

    /**
     * Attaches the LDLib2 resource event listener to the mod event bus before the editor is opened.
     *
     * @param modEventBus client mod event bus used by LDLib2 to publish editor resource events
     */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(DataEnergisticsTextureEditorResources::loadBuiltinResources);
    }

    /**
     * Adds the project's texture library after LDLib2 creates its built-in texture providers.
     *
     * @param event LDLib2 built-in resource event
     */
    private static void loadBuiltinResources(EditorResourceEvent.LoadBuiltin event) {
        if (event.resourceInstance.resource != TexturesResource.INSTANCE) {
            return;
        }

        ResourceInstance<IGuiTexture> resourceInstance = textureResourceInstance(event);
        var provider = new BuiltinResourceProvider<>(Data_Energistics.MODID, resourceInstance);
        registerComposedTextures(provider);
        registerTextureAssets(provider);
        resourceInstance.addBuiltinProvider(provider);
    }

    /**
     * Obtains the texture resource instance currently being built by LDLib2 without triggering its
     * lazy initialization again from inside the LoadBuiltin callback.
     *
     * @param event LDLib2 built-in resource event for the texture resource
     * @return the resource instance carried by the event
     */
    @SuppressWarnings("unchecked")
    private static ResourceInstance<IGuiTexture> textureResourceInstance(EditorResourceEvent.LoadBuiltin event) {
        return (ResourceInstance<IGuiTexture>) event.resourceInstance;
    }

    /**
     * Registers the named compositions currently used by the LDLib2 Trinity and compartment UIs.
     *
     * @param provider provider receiving the composed textures
     */
    private static void registerComposedTextures(BuiltinResourceProvider<IGuiTexture> provider) {
        provider.addResource("trinity_root_background", GuiTextureGroup.of(
                new ColorRectTexture(0xFFE3E3EA),
                new ColorBorderTexture(-1, 0xFF696D88)));
        provider.addResource("trinity_player_slot_background", GuiTextureGroup.of(
                guiSprite("inventory_slot.png"),
                new ColorBorderTexture(-1, 0xFF696D88)));
        provider.addResource("trinity_section_background", GuiTextureGroup.of(
                new ColorRectTexture(0xFFA7ADBF),
                new ColorBorderTexture(-1, 0xFFF2F2F2)));
        provider.addResource("composite_warehouse_sidebar_background", GuiTextureGroup.of(
                new ColorRectTexture(0xFFE3E3EA),
                new ColorBorderTexture(-1, 0xFF777784)));
        provider.addResource("composite_warehouse_optional_fluid_slot", SpriteTexture
                .of("ae2:textures/guis/composite_warehouse.png")
                .setSprite(133, 28, 18, 18));
        provider.addResource("composite_warehouse_optional_key_slot", SpriteTexture
                .of("ae2:textures/guis/composite_warehouse.png")
                .setSprite(151, 28, 18, 18));
        provider.addResource("pattern_buffer_empty_slot", SpriteTexture
                .of("ae2:textures/guis/states.png")
                .setSprite(240, 128, 16, 16));
    }

    /**
     * Registers the project's raw PNG assets so they can be selected as SpriteTexture sources.
     *
     * @param provider provider receiving the raw texture assets
     */
    private static void registerTextureAssets(BuiltinResourceProvider<IGuiTexture> provider) {
        provider.addResource("autobuild_background", autobuildSprite("background.png"));
        provider.addResource("autobuild_build", autobuildSprite("build.png"));
        provider.addResource("autobuild_detailed_adjustment", autobuildSprite("detailed_adjustment.png"));
        provider.addResource("autobuild_detailed_material_stats", autobuildSprite("detailed_material_stats.png"));
        provider.addResource("autobuild_structure_switch", autobuildSprite("structure_switch.png"));
        provider.addResource("autobuild_structure_view", autobuildSprite("structure_view.png"));
        provider.addResource("gui_back", guiSprite("back.png"));
        provider.addResource("gui_button", guiSprite("botton.png"));
        provider.addResource("gui_button_disabled", guiSprite("button_disabled.png"));
        provider.addResource("gui_button_highlighted", guiSprite("button_highlighted.png"));
        provider.addResource("gui_front", guiSprite("front.png"));
        provider.addResource("gui_inventory_slot", guiSprite("inventory_slot.png"));
        provider.addResource("gui_small_highlighted", guiSprite("small_highlighted.png"));
        provider.addResource("gui_small_scroller", guiSprite("small_scroller.png"));
        provider.addResource("gui_small_scroller_disabled", guiSprite("small_scroller_disabled.png"));
        provider.addResource("trinity_cpu_entry", sprite("cpu_entry.png"));
        provider.addResource("trinity_cpu_entry_selected", sprite("cpu_entry_selected.png"));
        provider.addResource("trinity_cpu_icon_craft", sprite("cpu_icon_craft.png"));
        provider.addResource("trinity_cpu_icon_machine", sprite("cpu_icon_machine.png"));
        provider.addResource("trinity_cpu_icon_processor", sprite("cpu_icon_processor.png"));
        provider.addResource("trinity_cpu_icon_storage", sprite("cpu_icon_storage.png"));
        provider.addResource("trinity_cpu_icon_terminal", sprite("cpu_icon_terminal.png"));
        provider.addResource("trinity_cpu_idle", sprite("cpu_idle.png"));
        provider.addResource("trinity_cpu_panel", sprite("cpu_panel.png"));
        provider.addResource("trinity_cpu_task_overlay", sprite("cpu_task_overlay.png"));
        provider.addResource("trinity_host_layout_reference", sprite("host_layout_reference.png"));
        provider.addResource("trinity_status_panel", sprite("status_panel.png"));
        provider.addResource("trinity_storage_capacity_track", sprite("storage_capacity_track.png"));
        provider.addResource("trinity_storage_fluid_fill", sprite("storage_fluid_fill.png"));
        provider.addResource("trinity_storage_item_fill", sprite("storage_item_fill.png"));
        provider.addResource("trinity_storage_other_fill", sprite("storage_other_fill.png"));
    }

    /**
     * Creates an editable LDLib2 SpriteTexture for a Data Energistics GUI asset.
     *
     * @param fileName asset file under the Trinity Data Core GUI texture directory
     * @return SpriteTexture pointing at the mod asset
     */
    private static SpriteTexture sprite(String fileName) {
        return SpriteTexture.of(TRINITY_TEXTURE_ROOT + fileName);
    }

    /**
     * Creates an editable LDLib2 SpriteTexture for an automatic-build GUI asset.
     *
     * @param fileName asset file under the automatic-build GUI texture directory
     * @return SpriteTexture pointing at the mod asset
     */
    private static SpriteTexture autobuildSprite(String fileName) {
        return SpriteTexture.of(AUTO_BUILD_TEXTURE_ROOT + fileName);
    }

    /**
     * Creates an editable LDLib2 SpriteTexture for a top-level GUI asset.
     *
     * @param fileName asset file directly under the GUI texture directory
     * @return SpriteTexture pointing at the mod asset
     */
    private static SpriteTexture guiSprite(String fileName) {
        return SpriteTexture.of(GUI_TEXTURE_ROOT + fileName);
    }
}
