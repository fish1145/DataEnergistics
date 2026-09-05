package com.fish_dan_.data_energistics.client.screen.patternencoding;

import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;

import appeng.client.gui.style.ScreenStyle;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;

import it.unimi.dsi.fastutil.objects.ObjectList;

/**
 * Narrow client-thread contract used by the shared physical-provider drill-down panel.
 *
 * <p>
 * The host remains authoritative for screen geometry, widget registration and parent-row selection. The panel owns
 * only its local UI state and never mutates a menu snapshot.
 * </p>
 */
interface PatternProviderLeafPanelHost {

    PatternEncodingPreviewMenu leafPanelMenu();

    Font leafPanelFont();

    ScreenStyle leafPanelStyle();

    int leafPanelScreenWidth();

    int leafPanelScreenHeight();

    int leafPanelGuiLeft();

    int leafPanelGuiTop();

    Rect2i leafPanelParentBounds();

    ObjectList<Rect2i> leafPanelOccupiedZones();

    boolean leafPanelUploadEnabled();

    boolean leafPanelOpenEnabled();

    boolean leafPanelRenameEnabled();

    <W extends AbstractWidget> W registerLeafPanelWidget(W widget);

    void selectRenamedProviderLeaf(String providerDigest);
}
