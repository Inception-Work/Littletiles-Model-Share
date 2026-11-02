package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.BlueprintClient;
import work.inception.ltsmodelshare.client.ImageDraw;
import work.inception.ltsmodelshare.client.TextUtil;

import java.util.ArrayList;
import java.util.List;

/** Work detail with favorite action; preview and blueprint actions follow in later stages. */
public class GuiWorkDetail extends Screen {
    private final Screen parent;
    private final Dtos.WorkCard card;
    private final String accessCode;
    private Dtos.WorkDetail detail;
    private final List<Dtos.Item> items = new ArrayList<>();
    private boolean favorited;
    private boolean favoriteBusy;
    private boolean loading;
    private String status = "";

    public GuiWorkDetail(Dtos.WorkCard card, Screen parent) {
        this(card, null, parent);
    }

    public GuiWorkDetail(Dtos.WorkCard card, String accessCode, Screen parent) {
        super(Component.translatable("ltsmodelshare.detail.title"));
        this.card = card;
        this.accessCode = accessCode;
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        int backX = GuiNavigation.backX(width);
        addRenderableWidget(GuiNavigation.button(backX, GuiNavigation.footerY(height), GuiNavigation.BACK_WIDTH, 20,
                "ltsmodelshare.common.back", () -> minecraft.setScreen(parent)));
        int favoriteW = 64;
        addRenderableWidget(new FitButton(backX - GuiNavigation.GAP - favoriteW, GuiNavigation.footerY(height),
                favoriteW, 20, Component.translatable(favoriteKey()), b -> toggleFavorite()));
        addItemButtons();
        load();
    }

    private void addItemButtons() {
        int colW = Math.min(340, width - 20);
        int left = width / 2 - colW / 2;
        int coverW = Math.max(80, colW * 42 / 100);
        int coverH = coverW * 3 / 4;
        int listTop = 28 + coverH + 12;
        int rowY = listTop + 12;
        int maxRows = Math.max(0, (GuiNavigation.footerY(height) - 8 - rowY) / 34);
        for (int i = 0; i < Math.min(items.size(), maxRows); i++) {
            final Dtos.Item item = items.get(i);
            addRenderableWidget(new FitButton(left + colW - 76, rowY + 2, 72, 14,
                    Component.translatable("ltsmodelshare.detail.get_blueprint"), b -> fetchBlueprint(item)));
            if (item.preview != null && item.preview.available
                    && item.preview.assetUrl != null && !item.preview.assetUrl.isEmpty()) {
                addRenderableWidget(new FitButton(left + colW - 128, rowY + 2, 48, 14,
                        Component.translatable("ltsmodelshare.detail.preview"), b -> openPreview(item)));
            }
            rowY += 34;
        }
    }

    private void fetchBlueprint(Dtos.Item item) {
        if (!ModConfig.get().loggedIn()) {
            minecraft.setScreen(new GuiLogin(this));
            return;
        }
        if (minecraft.player == null) {
            status = tr("ltsmodelshare.detail.world_required");
            return;
        }
        status = tr("ltsmodelshare.detail.requested");
        ApiClient.downloadInfo(item.itemId, "1.19", accessCode, new ApiClient.Callback<Dtos.DownloadInfo>() {
            @Override public void ok(Dtos.DownloadInfo info) {
                if (info.version == null || !(info.version.startsWith("1.19") || info.version.startsWith("1.20"))) {
                    ApiClient.post(() -> status = tr("ltsmodelshare.preview.version"));
                    return;
                }
                ApiClient.fetchText(info, new ApiClient.Callback<String>() {
                    @Override public void ok(String text) {
                        ApiClient.post(() -> BlueprintClient.request(text, item.title));
                    }
                    @Override public void fail(String message) {
                        ApiClient.post(() -> status = message);
                    }
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> status = message);
            }
        });
    }

    private void openPreview(Dtos.Item item) {
        status = tr("ltsmodelshare.preview.obj_loading");
        ApiClient.fetchObj(item.preview.assetUrl, 8L * 1024 * 1024, new ApiClient.Callback<String>() {
            @Override public void ok(String text) {
                try {
                    ObjModel model = ObjModel.parse(text);
                    ApiClient.post(() -> minecraft.setScreen(new GuiObjPreview(GuiWorkDetail.this, model, item.title)));
                } catch (Exception error) {
                    ApiClient.post(() -> status = tr("ltsmodelshare.preview.obj_failed", "", error.getMessage()));
                }
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> status = tr("ltsmodelshare.preview.obj_failed", "", message));
            }
        });
    }

    /** Server result callback (client thread). */
    public void onServerBlueprint(boolean ok, String text) {
        status = text;
    }

    private String favoriteKey() {
        return favorited ? "ltsmodelshare.common.favorited" : "ltsmodelshare.common.favorite";
    }

    private void refreshFavoriteLabel() {
        // Rebuild only the favorite button label by recreating widgets.
        init();
    }

    private void load() {
        if (loading || detail != null) return;
        loading = true;
        status = tr("ltsmodelshare.common.loading");
        ApiClient.detail(card.workId, accessCode, new ApiClient.Callback<Dtos.WorkDetail>() {
            @Override public void ok(Dtos.WorkDetail value) {
                ApiClient.post(() -> {
                    loading = false;
                    detail = value;
                    items.clear();
                    if (value.items != null) items.addAll(value.items);
                    favorited = Boolean.TRUE.equals(value.favorited);
                    status = items.isEmpty() ? tr("ltsmodelshare.detail.no_models") : "";
                    init(); // adds per-item blueprint buttons; load() is guarded by detail != null
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> {
                    loading = false;
                    status = message;
                });
            }
        });
    }

    private void toggleFavorite() {
        if (!ModConfig.get().loggedIn()) {
            minecraft.setScreen(new GuiLogin(this));
            return;
        }
        if (favoriteBusy) return;
        favoriteBusy = true;
        ApiClient.toggleFavorite(card.workId, new ApiClient.Callback<Dtos.SocialToggle>() {
            @Override public void ok(Dtos.SocialToggle value) {
                ApiClient.post(() -> {
                    favoriteBusy = false;
                    if (value != null && value.favorited != null) {
                        favorited = value.favorited;
                        init();
                    }
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> {
                    favoriteBusy = false;
                    status = message;
                });
            }
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int cx = width / 2;
        int colW = Math.min(340, width - 20);
        int left = cx - colW / 2;

        String title = detail != null && detail.title != null ? detail.title : card.title;
        int coverW = Math.max(80, colW * 42 / 100);
        int coverH = coverW * 3 / 4;
        String cover = detail != null && detail.images != null && !detail.images.isEmpty()
                ? detail.images.get(0) : card.cover;
        ImageDraw.drawPreview(graphics, cover, left, 28, coverW, coverH);

        int textX = left + coverW + 10;
        int textW = Math.max(60, colW - coverW - 10);
        graphics.drawString(font, TextUtil.trim(font, title == null ? tr("ltsmodelshare.common.untitled") : title, textW),
                textX, 30, 0xFFFFFF, true);
        String summary = detail != null ? detail.summary : card.summary;
        int y = 44;
        for (var line : TextUtil.wrap(font, summary == null ? "" : summary, textW, 5)) {
            graphics.drawString(font, line, textX, y, 0xCCCCCC, false);
            y += 10;
        }
        if (detail != null) {
            graphics.drawString(font, tr("ltsmodelshare.detail.stats", detail.viewCount, detail.downloadCount,
                            detail.favoriteCount == null ? 0 : detail.favoriteCount,
                            detail.commentCount == null ? 0 : detail.commentCount),
                    textX, y + 4, 0xAAAAAA, false);
        }

        int listTop = 28 + coverH + 12;
        graphics.drawString(font, tr("ltsmodelshare.detail.models"), left, listTop, 0xFFCC88, false);
        int rowY = listTop + 12;
        int rowH = 34;
        int maxRows = Math.max(0, (GuiNavigation.footerY(height) - 8 - rowY) / rowH);
        for (int i = 0; i < Math.min(items.size(), maxRows); i++) {
            Dtos.Item item = items.get(i);
            graphics.fill(left, rowY, left + colW, rowY + rowH - 2, 0x33000000);
            String itemTitle = item.title == null ? tr("ltsmodelshare.common.untitled") : item.title;
            graphics.drawString(font, TextUtil.trim(font, itemTitle, colW - 100), left + 4, rowY + 4, 0xFFFFFF, true);
            String meta = (item.mcVersion == null ? "" : item.mcVersion)
                    + (item.boxCount == null ? "" : " · " + item.boxCount);
            graphics.drawString(font, TextUtil.trim(font, meta, colW - 100), left + 4, rowY + 16, 0xAAAAAA, false);
            rowY += rowH;
        }
        if (!status.isEmpty()) {
            graphics.drawCenteredString(font, TextUtil.trim(font, status, width - 24), cx,
                    Math.max(60, height - 46), 0xFFFFFF);
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
