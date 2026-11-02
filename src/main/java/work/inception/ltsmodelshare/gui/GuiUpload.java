package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import team.creative.littletiles.common.block.little.tile.group.LittleGroup;
import team.creative.littletiles.common.item.ItemLittleBlueprint;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.TextUtil;

import java.util.Collections;

/**
 * Upload the held blueprint as a work. Source loader/version, locale and visibility are
 * filled automatically; screenshot/tag editing is a later parity step.
 */
public class GuiUpload extends Screen {
    private static final String SOURCE_LOADER = "forge";
    private static final String GAME_VERSION = "1.20.1";

    private final Screen parent;
    private EditBox titleField;
    private EditBox summaryField;
    private String snbt;
    private String exportError;
    private boolean visibilityPublic = true;
    private String accountLocale;
    private boolean localeRequested;
    private boolean busy;
    private boolean submitted;
    private String status = "";

    public GuiUpload(Screen parent) {
        super(Component.translatable("ltsmodelshare.upload.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        int panelW = Math.min(340, width - 24);
        int left = width / 2 - panelW / 2;
        int y = 44;
        titleField = new EditBox(font, left, y + 10, panelW, 18,
                Component.translatable("ltsmodelshare.upload.field_title"));
        titleField.setMaxLength(64);
        addRenderableWidget(titleField);
        y += 36;
        summaryField = new EditBox(font, left, y + 10, panelW, 18,
                Component.translatable("ltsmodelshare.upload.summary"));
        summaryField.setMaxLength(512);
        addRenderableWidget(summaryField);

        int footerY = GuiNavigation.footerY(height);
        addRenderableWidget(new FitButton(left, footerY - 24, 64, 18,
                Component.translatable("ltsmodelshare.upload.visibility_public"),
                b -> setVisibility(true)));
        addRenderableWidget(new FitButton(left + 70, footerY - 24, 64, 18,
                Component.translatable("ltsmodelshare.upload.visibility_private"),
                b -> setVisibility(false)));
        addRenderableWidget(GuiNavigation.button(left, footerY, 84, 20,
                "ltsmodelshare.upload.draft", () -> startUpload(false)));
        addRenderableWidget(GuiNavigation.button(left + 90, footerY, 84, 20,
                "ltsmodelshare.upload.review", () -> startUpload(true)));
        addRenderableWidget(GuiNavigation.button(GuiNavigation.backX(width), footerY, GuiNavigation.BACK_WIDTH, 20,
                "ltsmodelshare.common.back", () -> minecraft.setScreen(parent)));

        exportFromHand();
        if (!localeRequested) {
            localeRequested = true;
            ApiClient.accountLocale(new ApiClient.Callback<String>() {
                @Override public void ok(String value) {
                    ApiClient.post(() -> accountLocale = value);
                }
                @Override public void fail(String message) { }
            });
        }
    }

    private void setVisibility(boolean isPublic) {
        visibilityPublic = isPublic;
    }

    private void exportFromHand() {
        snbt = null;
        exportError = null;
        if (minecraft.player == null) {
            exportError = tr("ltsmodelshare.common.world_required");
            return;
        }
        ItemStack held = minecraft.player.getMainHandItem();
        if (held.isEmpty()) {
            exportError = tr("ltsmodelshare.upload.hold_blueprint");
            return;
        }
        if (!(held.getItem() instanceof ItemLittleBlueprint blueprint)) {
            exportError = tr("ltsmodelshare.upload.not_blueprint");
            return;
        }
        try {
            LittleGroup group = blueprint.getTiles(held);
            if (group == null || group.size() == 0) {
                exportError = tr("ltsmodelshare.upload.no_structure");
                return;
            }
            snbt = LittleGroup.save(group).toString();
            if (titleField != null && titleField.getValue().isEmpty()) {
                titleField.setValue(held.getHoverName().getString());
            }
        } catch (Throwable error) {
            exportError = tr("ltsmodelshare.upload.export_failed", error.toString());
        }
    }

    private void startUpload(final boolean alsoSubmit) {
        if (busy || submitted) return;
        if (snbt == null) {
            status = exportError == null ? tr("ltsmodelshare.upload.no_structure") : exportError;
            return;
        }
        String title = titleField == null ? "" : titleField.getValue().trim();
        if (title.isEmpty()) {
            status = tr("ltsmodelshare.upload.title_required");
            return;
        }
        busy = true;
        status = tr("ltsmodelshare.upload.structure");
        ApiClient.uploadSnbt(snbt, title, new ApiClient.Callback<Dtos.SnbtUpload>() {
            @Override public void ok(Dtos.SnbtUpload blob) {
                ApiClient.post(() -> saveWork(blob, title, alsoSubmit));
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> { busy = false; status = message; });
            }
        });
    }

    private void saveWork(Dtos.SnbtUpload blob, String title, final boolean alsoSubmit) {
        status = tr("ltsmodelshare.upload.saving");
        Dtos.WorkSaveRequest request = new Dtos.WorkSaveRequest();
        request.kind = "single";
        request.visibility = visibilityPublic ? "public" : "private";
        request.sourceLoader = SOURCE_LOADER;
        request.sourceGameVersion = GAME_VERSION;
        request.sourceLocale = accountLocale != null && !accountLocale.trim().isEmpty()
                ? accountLocale : work.inception.ltsmodelshare.client.Translations.siteLocale();
        request.title = title;
        String summary = summaryField == null ? "" : summaryField.getValue().trim();
        request.summary = summary.isEmpty() ? null : summary;
        Dtos.WorkSaveRequest.SaveItem item = new Dtos.WorkSaveRequest.SaveItem(blob.blobId, title, GAME_VERSION);
        request.items = Collections.singletonList(item);
        ApiClient.saveWork(request, new ApiClient.Callback<Long>() {
            @Override public void ok(Long workId) {
                ApiClient.post(() -> {
                    if (!alsoSubmit) {
                        busy = false;
                        submitted = true;
                        status = tr("ltsmodelshare.upload.draft_saved", workId, 0, 0);
                        return;
                    }
                    status = tr("ltsmodelshare.upload.consent");
                    ApiClient.agreeUploadRights(new ApiClient.Callback<Long>() {
                        @Override public void ok(Long consentId) {
                            status = tr("ltsmodelshare.upload.submitting");
                            ApiClient.submitReview(workId, false, consentId, new ApiClient.Callback<Dtos.SubmitReview>() {
                                @Override public void ok(Dtos.SubmitReview value) {
                                    ApiClient.post(() -> {
                                        busy = false;
                                        submitted = true;
                                        status = value.submitted
                                                ? tr("ltsmodelshare.upload.submitted", workId)
                                                : tr("ltsmodelshare.upload.auto_submit", workId);
                                    });
                                }
                                @Override public void fail(String message) {
                                    ApiClient.post(() -> {
                                        busy = false;
                                        status = tr("ltsmodelshare.upload.review_error", workId, message);
                                    });
                                }
                            });
                        }
                        @Override public void fail(String message) {
                            ApiClient.post(() -> {
                                busy = false;
                                status = tr("ltsmodelshare.upload.consent_error", workId, message);
                            });
                        }
                    });
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> { busy = false; status = message; });
            }
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int panelW = Math.min(340, width - 24);
        int left = width / 2 - panelW / 2;
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        String subtitle = snbt == null
                ? (exportError == null ? "" : exportError)
                : tr("ltsmodelshare.upload.exported", snbt.length());
        graphics.drawString(font, TextUtil.trim(font, subtitle, panelW), left, 30, 0xAAAAAA, false);
        graphics.drawString(font, tr("ltsmodelshare.upload.field_title"), left, 44, 0xCCCCCC, false);
        graphics.drawString(font, tr("ltsmodelshare.upload.summary"), left, 80, 0xCCCCCC, false);
        int footerY = GuiNavigation.footerY(height);
        int selectedX = visibilityPublic ? left : left + 70;
        graphics.fill(selectedX, footerY - 6, selectedX + 64, footerY - 4, 0xFFFFFFFF);
        if (!status.isEmpty()) {
            graphics.drawCenteredString(font, TextUtil.trim(font, status, width - 24), width / 2,
                    footerY - 44, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
