package ru.dez.client.camera;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector2d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "blocks_blades", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RTSHudRenderer {

    /**
     * Tooltips are drawn last so later buttons/icons can never paint over their
     * text. Populated during the frame and executed at the end of onRenderGui.
     */
    private static Runnable pendingTooltip = null;

    private static final int COL_TASK = 36;
    private static final int COL_BUILD = 78;
    private static final int BTN = 28;
    private static final int GAP = 32;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!RTSCameraManager.isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.screen != null || mc.level == null || mc.player == null) {
            return;
        }

        // Safety net in case the owned-set sync was missed: if RTS villagers
        // are loaded, the village exists and the HUD must not fall back to its
        // initial "spawn villagers" state.
        if (!RTSClientState.hasVillagers && !RTSCameraUtil.getAllUnits(mc.level).isEmpty()) {
            RTSClientState.hasVillagers = true;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int width = RTSCameraUtil.getGuiWidth();
        int height = RTSCameraUtil.getGuiHeight();
        Vector2d mouse = RTSCameraUtil.getMousePos();

        pendingTooltip = null;

        RTSClientState.unitButtons.clear();
        RTSClientState.unitIds.clear();

        RTSTaskClient.taskButtons.clear();
        RTSTaskClient.taskActions.clear();

        RTSBuildClient.buildButtons.clear();
        RTSBuildClient.buildActions.clear();

        RTSClientState.profButtons.clear();
        RTSClientState.profActions.clear();

        RTSClientState.teamButtons.clear();
        RTSClientState.teamActions.clear();

        RTSClientState.townHallButtons.clear();
        RTSClientState.townHallActions.clear();

        if (!RTSClientState.hasVillagers) {
            drawVillagerButton(graphics, mc, mouse);
        } else {
            drawResources(graphics, mc);
            drawProfessionButton(graphics, mc, width, height, mouse);

            drawTaskMenuButton(graphics, mc, width, mouse);
            drawBuildMenuButton(graphics, mc, width, mouse);

            if (RTSTaskClient.taskPanelOpen) {
                drawTaskPanel(graphics, mc, width, mouse);
            }

            if (RTSBuildClient.buildPanelOpen) {
                drawBuildPanel(graphics, mc, width, mouse);
            }

            if (RTSClientState.professionPanelOpen) {
                drawProfessionPanel(graphics, mc, width, height, mouse);
            }
        }

        drawTeamButton(graphics, mc, width, mouse);

        if (RTSClientState.teamMenuOpen) {
            drawTeamMenu(graphics, mc, width, mouse);
        }

        if (RTSClientState.townHallPanelOpen) {
            drawTownHallPanel(graphics, mc, width, height, mouse);
        }

        drawSelectionPanel(graphics, mc, width, height, mouse);
        drawTownHallUpgrade(graphics, mc, width, height, mouse);

        if (RTSClientState.placementMode) {
            drawCentered(graphics, mc, "ЛКМ - отмена, ПКМ - поставить", width / 2, height - 100, 0xFFFFFF00);
        }

        if (RTSBuildClient.placing) {
            drawCentered(graphics, mc, "ЛКМ - отмена, ПКМ - поставить стройплощадку", width / 2, height - 116, 0xFFFFFF00);
        }

        if (RTSBuildClient.message != null) {
            drawCentered(graphics, mc, RTSBuildClient.message, width / 2, height - 132, 0xFFFF5252);
        }

        // Tooltips go on top of everything else.
        if (pendingTooltip != null) {
            pendingTooltip.run();
            pendingTooltip = null;
        }
    }

    private static void drawVillagerButton(GuiGraphics graphics, Minecraft mc, Vector2d mouse) {
        int x = 8;
        int y = 8;
        int w = 28;
        int h = 28;

        RTSClientState.villagerButton = new RTSClientState.Rect(x, y, w, h);

        boolean hovered = RTSClientState.villagerButton.contains(mouse.x, mouse.y);

        drawStyledButton(graphics, x, y, w, h, hovered, RTSClientState.placementMode);
        graphics.renderItem(new ItemStack(Items.VILLAGER_SPAWN_EGG), x + 6, y + 6);
    }

    /** Top-right "+" button that opens the team / invite panel. */
    private static void drawTeamButton(GuiGraphics graphics, Minecraft mc, int width, Vector2d mouse) {
        int size = 28;
        // Sit to the LEFT of the build/task buttons so we do not cover them.
        int x = width - COL_BUILD - GAP;
        int y = 8;

        RTSClientState.teamButton = new RTSClientState.Rect(x, y, size, size);

        boolean hovered = RTSClientState.teamButton.contains(mouse.x, mouse.y);

        drawStyledButton(graphics, x, y, size, size, hovered, RTSClientState.teamMenuOpen);
        graphics.drawCenteredString(mc.font, "+", x + size / 2, y + 10, 0xFFFFFF);
    }

    /** Panel listing the village members, pending invite and online players. */
    private static void drawTeamMenu(GuiGraphics graphics, Minecraft mc, int width, Vector2d mouse) {
        int panelW = 250;
        int x = width - panelW - 8;
        int y = 42;
        int rowH = 22;
        int rows = RTSTeamClient.onlineIds.size();

        int panelH = 40 + rows * rowH;

        if (RTSTeamClient.hasInvite()) {
            panelH += rowH;
        }

        if (RTSTeamClient.memberIds.size() > 1) {
            panelH += rowH;
        }

        drawPanel(graphics, x, y, panelW, panelH);

        int cy = y + 6;
        graphics.drawString(mc.font, "Деревня: " + trim(RTSTeamClient.villageName, 18)
                + " (" + RTSTeamClient.memberIds.size() + ")", x + 6, cy, 0xFF00E676);
        cy += 16;

        if (RTSTeamClient.hasInvite()) {
            graphics.drawString(mc.font, "Зовут: " + trim(RTSTeamClient.inviteVillageName, 12), x + 6, cy + 4, 0xFFFFFF00);
            addTeamButton(graphics, mc, mouse, x + panelW - 132, cy, 60, 16, "Принять", "accept");
            addTeamButton(graphics, mc, mouse, x + panelW - 68, cy, 60, 16, "Отклонить", "decline");
            cy += rowH;
        }

        if (RTSTeamClient.memberIds.size() > 1) {
            addTeamButton(graphics, mc, mouse, x + 6, cy, 130, 16, "Покинуть деревню", "leave");
            cy += rowH;
        }

        graphics.drawString(mc.font, "Игроки онлайн:", x + 6, cy, 0xFFBFBFBF);
        cy += 14;

        String self = mc.player != null ? mc.player.getUUID().toString() : "";

        for (int i = 0; i < RTSTeamClient.onlineIds.size(); i++) {
            String id = RTSTeamClient.onlineIds.get(i).toString();
            String name = trim(RTSTeamClient.onlineNames.get(i), 14);

            if (id.equals(self)) {
                graphics.drawString(mc.font, name + " (ты)", x + 6, cy + 4, 0xFF9E9E9E);
            } else {
                graphics.drawString(mc.font, name, x + 6, cy + 4, 0xFFFFFFFF);

                if (RTSTeamClient.isMember(i)) {
                    graphics.drawString(mc.font, "в деревне", x + panelW - 66, cy + 4, 0xFF808080);
                } else {
                    addTeamButton(graphics, mc, mouse, x + panelW - 200, cy, 62, 16, "Позвать", "invite:" + id);

                    boolean war = RTSTeamClient.warsWith.contains(RTSTeamClient.onlineVillages.get(i));
                    addTeamButton(graphics, mc, mouse, x + panelW - 134, cy, 62, 16, war ? "Мир" : "Война", "war:" + id);
                }
            }

            cy += rowH;
        }
    }

    private static void addTeamButton(GuiGraphics graphics, Minecraft mc, Vector2d mouse,
                                      int x, int y, int w, int h, String label, String action) {
        boolean hovered = mouse.x >= x && mouse.x <= x + w && mouse.y >= y && mouse.y <= y + h;

        drawStyledButton(graphics, x, y, w, h, hovered, false);

        int textWidth = mc.font.width(label);
        graphics.drawString(mc.font, label, x + (w - textWidth) / 2, y + (h - 8) / 2, 0xFFFFFF);

        RTSClientState.teamButtons.add(new RTSClientState.Rect(x, y, w, h));
        RTSClientState.teamActions.add(action);
    }

    /** Hire panel shown when the player clicks a town hall in the world. */
    private static void drawTownHallPanel(GuiGraphics graphics, Minecraft mc, int width, int height, Vector2d mouse) {
        int w = 230;
        int h = 72;
        int x = width / 2 - w / 2;
        int y = height - 110 - h;

        if (y < 6) {
            y = 6;
        }

        drawPanel(graphics, x, y, w, h);

        graphics.drawString(mc.font, "Мэрия", x + 8, y + 7, 0xFF00E676);
        graphics.drawString(mc.font, "В очереди: " + RTSClientState.hireQueue, x + 8, y + 21, 0xFFFFFFFF);
        graphics.drawString(mc.font, "Новый житель каждые 30 секунд", x + 8, y + 34, 0xFF9E9E9E);

        addTownHallButton(graphics, mc, mouse, x + 8, y + h - 24, 150, 18,
                "Нанять жителя - " + RTSClientState.hireCost + " еды", "hire");
        addTownHallButton(graphics, mc, mouse, x + w - 70, y + h - 24, 62, 18, "Закрыть", "close");
    }

    private static void addTownHallButton(GuiGraphics graphics, Minecraft mc, Vector2d mouse,
                                          int x, int y, int w, int h, String label, String action) {
        boolean hovered = mouse.x >= x && mouse.x <= x + w && mouse.y >= y && mouse.y <= y + h;

        drawStyledButton(graphics, x, y, w, h, hovered, false);

        int textWidth = mc.font.width(label);
        graphics.drawString(mc.font, label, x + (w - textWidth) / 2, y + (h - 8) / 2, 0xFFFFFF);

        RTSClientState.townHallButtons.add(new RTSClientState.Rect(x, y, w, h));
        RTSClientState.townHallActions.add(action);
    }

    private static void drawResources(GuiGraphics graphics, Minecraft mc) {
        int x = 8;
        int y = 8;

        // Each plate grows with its number so large values never get clipped.
        x += drawResource(graphics, mc, x, y, Items.OAK_LOG, RTSClientState.wood);
        x += drawResource(graphics, mc, x, y, Items.BREAD, RTSClientState.food);
        x += drawResource(graphics, mc, x, y, Items.IRON_INGOT, RTSClientState.ore);
        drawResource(graphics, mc, x, y, Items.EMERALD, RTSClientState.happiness);
    }

    /** Draws one resource plate and returns its total width (including gap). */
    private static int drawResource(GuiGraphics graphics, Minecraft mc, int x, int y, Item item, int count) {
        String text = String.valueOf(count);
        int width = Math.max(44, 22 + mc.font.width(text) + 6);

        graphics.fill(x - 1, y - 1, x + width + 1, y + 19, 0xFF000000);
        fillGradientV(graphics, x, y, x + width, y + 18, 0xF01B2A1E, 0xF00A0F0A);
        graphics.renderItem(new ItemStack(item), x + 2, y + 1);
        graphics.drawString(mc.font, text, x + 22, y + 5, 0xFFE8F5E9);

        // Gently pulsing border so the plates feel alive.
        graphics.renderOutline(x, y, width, 18, lerpColor(0xCC00E676, 0xFF7CFFB2, pulse(1.5F)));

        return width + 6;
    }

    private static void drawProfessionButton(GuiGraphics graphics, Minecraft mc, int width, int height, Vector2d mouse) {
        int size = 28;
        int x = width / 2 - size / 2;
        int y = height - size - 8;

        RTSClientState.professionButton = new RTSClientState.Rect(x, y, size, size);

        boolean hovered = RTSClientState.professionButton.contains(mouse.x, mouse.y);

        drawStyledButton(graphics, x, y, size, size, hovered, RTSClientState.professionPanelOpen);
        graphics.renderItem(new ItemStack(Items.NAME_TAG), x + 6, y + 6);
    }

    /**
     * The profession tree: one column per profession, with the miner and
     * lumberjack branches expanding into upgrade stages below their hire node.
     * Node colours: grey = locked, green = available, amber = needs resources,
     * blue = already finished.
     */
    private static void drawProfessionPanel(GuiGraphics graphics, Minecraft mc, int width, int height, Vector2d mouse) {
        int node = 34;
        int colGap = 16;
        int rowGap = 18;
        int pad = 10;
        int header = 16;
        int cols = 6;
        int rows = 3;

        int w = pad * 2 + cols * node + (cols - 1) * colGap;
        int h = pad * 2 + header + rows * node + (rows - 1) * rowGap;

        int x = width / 2 - w / 2;
        int y = height - 58 - h;

        if (y < 6) {
            y = 6;
        }

        drawPanel(graphics, x, y, w, h);

        int baseX = x + pad;
        int baseY = y + pad + header;

        int minerX = baseX;
        int lumberX = baseX + node + colGap;
        int farmerX = baseX + (node + colGap) * 2;
        int warriorX = baseX + (node + colGap) * 3;
        int arbalestX = baseX + (node + colGap) * 4;
        int summonerX = baseX + (node + colGap) * 5;

        int row0 = baseY;
        int row1 = baseY + node + rowGap;
        int row2 = baseY + (node + rowGap) * 2;

        drawColumnCaption(graphics, mc, "Шахтёр", minerX, node, y);
        drawColumnCaption(graphics, mc, "Лесоруб", lumberX, node, y);
        drawColumnCaption(graphics, mc, "Фермер", farmerX, node, y);
        drawColumnCaption(graphics, mc, "Воин", warriorX, node, y);
        drawColumnCaption(graphics, mc, "Арбал.", arbalestX, node, y);
        drawColumnCaption(graphics, mc, "Призыв.", summonerX, node, y);

        // Branch links for the two multi-stage professions.
        drawNodeLink(graphics, minerX + node / 2, row0 + node, row1);
        drawNodeLink(graphics, minerX + node / 2, row1 + node, row2);
        drawNodeLink(graphics, lumberX + node / 2, row0 + node, row1);
        drawNodeLink(graphics, lumberX + node / 2, row1 + node, row2);

        boolean minerHired = hasProfession(1);
        boolean lumberHired = hasProfession(2);

        drawTreeNode(graphics, mc, mouse, minerX, row0, node, Items.IRON_PICKAXE, "prof_miner",
                RTSBuildings.isCompleted("mine") ? 1 : 0);

        int miner2 = RTSBuildings.isCompleted("mine_2") ? 2
                : RTSBuildings.isCompleted("mine") && minerHired ? upgradeState("mine_2") : 0;
        drawTreeNode(graphics, mc, mouse, minerX, row1, node, Items.IRON_PICKAXE, "build:mine_2", miner2);

        int miner3 = RTSBuildings.isCompleted("mine_3") ? 2
                : RTSBuildings.isCompleted("mine_2") ? upgradeState("mine_3") : 0;
        drawTreeNode(graphics, mc, mouse, minerX, row2, node, Items.DIAMOND_PICKAXE, "build:mine_3", miner3);

        drawTreeNode(graphics, mc, mouse, lumberX, row0, node, Items.STONE_AXE, "prof_lumber",
                RTSBuildings.isCompleted("lumber_camp") ? 1 : 0);

        int lumber2 = RTSBuildings.isCompleted("lumber_camp_2") ? 2
                : RTSBuildings.isCompleted("lumber_camp") && lumberHired ? upgradeState("lumber_camp_2") : 0;
        drawTreeNode(graphics, mc, mouse, lumberX, row1, node, Items.IRON_AXE, "build:lumber_camp_2", lumber2);

        int lumber3 = RTSBuildings.isCompleted("lumber_camp_3") ? 2
                : RTSBuildings.isCompleted("lumber_camp_2") ? upgradeState("lumber_camp_3") : 0;
        drawTreeNode(graphics, mc, mouse, lumberX, row2, node, Items.DIAMOND_AXE, "build:lumber_camp_3", lumber3);

        drawTreeNode(graphics, mc, mouse, farmerX, row0, node, Items.WHEAT, "prof_farmer",
                RTSBuildings.isCompleted("farm") ? 1 : 0);
        drawTreeNode(graphics, mc, mouse, warriorX, row0, node, Items.IRON_SWORD, "prof_warrior",
                RTSBuildings.isCompleted("barracks") ? 1 : 0);
        drawTreeNode(graphics, mc, mouse, arbalestX, row0, node, Items.CROSSBOW, "prof_arbalest",
                RTSBuildings.isCompleted("barracks") ? 1 : 0);
        drawTreeNode(graphics, mc, mouse, summonerX, row0, node, Items.TOTEM_OF_UNDYING, "prof_summoner",
                RTSBuildings.isCompleted("barracks") ? 1 : 0);
    }

    private static void drawColumnCaption(GuiGraphics graphics, Minecraft mc, String text, int colX, int node, int panelY) {
        graphics.drawString(mc.font, text, colX + node / 2 - mc.font.width(text) / 2, panelY + 5, 0xFF9CCC65);
    }

    private static void drawNodeLink(GuiGraphics graphics, int centerX, int fromY, int toY) {
        graphics.fill(centerX, fromY, centerX + 2, toY, 0xFF555555);
    }

    private static boolean hasProfession(int prof) {
        return RTSProfClient.PROF.containsValue(prof);
    }

    /** 1 = available and affordable, 3 = available but missing resources. */
    private static int upgradeState(String buildingId) {
        RTSBuilding building = RTSBuildings.get(buildingId);

        if (building == null) {
            return 0;
        }

        return RTSBuildClient.canAfford(building) ? 1 : 3;
    }

    private static void drawTreeNode(GuiGraphics graphics, Minecraft mc, Vector2d mouse, int x, int y, int size, Item icon, String action, int state) {
        boolean hovered = mouse.x >= x && mouse.x <= x + size && mouse.y >= y && mouse.y <= y + size;

        if (state == 0) {
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF000000);
            graphics.fill(x, y, x + size, y + size, 0xFF1A1A1A);
        } else {
            drawStyledButton(graphics, x, y, size, size, hovered, state == 2);
        }

        graphics.renderItem(new ItemStack(icon), x + (size - 16) / 2, y + (size - 16) / 2);

        int color = state == 2 ? 0xFF64B5F6 : state == 1 ? 0xFF00E676 : state == 3 ? 0xFFFFC107 : 0xFF555555;
        graphics.renderOutline(x, y, size, size, state == 0 ? color : lerpColor(0xDD00E676, color, hovered ? 1.0F : pulse(1.5F)));

        if (state != 0) {
            RTSClientState.profButtons.add(new RTSClientState.Rect(x, y, size, size));
            RTSClientState.profActions.add(action);
        }

        if (hovered) {
            pendingTooltip = () -> showNodeTooltip(graphics, mc, x, y, action, state);
        }
    }

    private static void showNodeTooltip(GuiGraphics graphics, Minecraft mc, int btnX, int btnY, String action, int state) {
        if (action.startsWith("prof_")) {
            String label = action.equals("prof_miner") ? "Нанять шахтёра"
                    : action.equals("prof_lumber") ? "Нанять лесоруба"
                    : action.equals("prof_farmer") ? "Нанять фермера"
                    : action.equals("prof_arbalest") ? "Нанять арбалетчика"
                    : action.equals("prof_summoner") ? "Нанять призывателя"
                    : "Нанять воина";

            final String text = state == 0 ? label + " (нужно здание)" : label;

            pendingTooltip = () -> drawLabelTooltip(graphics, mc, btnX, btnY, text);
            return;
        }

        RTSBuilding building = RTSBuildings.get(action.substring(6));

        if (building == null) {
            return;
        }

        if (state == 0) {
            drawLabelTooltip(graphics, mc, btnX, btnY, building.name + ": нужен прошлый уровень");
        } else {
            drawBuildTooltip(graphics, mc, btnX, btnY, building);
        }
    }

    private static void drawTaskMenuButton(GuiGraphics graphics, Minecraft mc, int width, Vector2d mouse) {
        int x = width - COL_TASK;
        int y = 8;

        RTSClientState.Rect rect = new RTSClientState.Rect(x, y, BTN, BTN);
        boolean hovered = rect.contains(mouse.x, mouse.y);

        drawStyledButton(graphics, x, y, BTN, BTN, hovered, RTSTaskClient.taskPanelOpen);
        graphics.renderItem(new ItemStack(Items.MAP), x + 6, y + 6);

        RTSTaskClient.taskButtons.add(rect);
        RTSTaskClient.taskActions.add("toggle_task");

        if (hovered) {
            pendingTooltip = () -> drawLabelTooltip(graphics, mc, x, y, "Задачи");
        }
    }

    private static void drawBuildMenuButton(GuiGraphics graphics, Minecraft mc, int width, Vector2d mouse) {
        int x = width - COL_BUILD;
        int y = 8;

        RTSBuildClient.buildMenuButton = new RTSClientState.Rect(x, y, BTN, BTN);

        boolean hovered = RTSBuildClient.buildMenuButton.contains(mouse.x, mouse.y);

        drawStyledButton(graphics, x, y, BTN, BTN, hovered, RTSBuildClient.buildPanelOpen);
        graphics.renderItem(new ItemStack(Items.BRICK), x + 6, y + 6);

        if (hovered) {
            pendingTooltip = () -> drawLabelTooltip(graphics, mc, x, y, "Строительство");
        }
    }

    private static void drawTaskPanel(GuiGraphics graphics, Minecraft mc, int width, Vector2d mouse) {
        int x = width - COL_TASK;
        int baseY = 8 + GAP;

        drawTaskButton(graphics, mc, mouse, x, baseY, Items.WOODEN_PICKAXE, "mine_menu", RTSTaskClient.miningPanelOpen);
        drawTaskButton(graphics, mc, mouse, x, baseY + GAP, Items.OAK_LOG, "auto_wood", false);
        drawTaskButton(graphics, mc, mouse, x, baseY + GAP * 2, Items.BRICK, "build_task", false);
        drawTaskButton(graphics, mc, mouse, x, baseY + GAP * 3, Items.BOW, "auto_hunt", false);

        // Everything else opens below in the same column, so the task panel can
        // never overlap the build panel column.
        int next = baseY + GAP * 4;

        if (hasSelectedWarrior()) {
            drawTaskButton(graphics, mc, mouse, x, next, Items.SHIELD, "defend", false);
            next += GAP;
        }

        if (RTSTaskClient.miningPanelOpen) {
            drawTaskButton(graphics, mc, mouse, x, next, Items.COBBLESTONE, "auto_cobble", false);
            next += GAP;
            drawTaskButton(graphics, mc, mouse, x, next, Items.COAL, "auto_coal", false);
            next += GAP;

            if (RTSBuildings.isCompleted("mine")) {
                drawTaskButton(graphics, mc, mouse, x, next, Items.IRON_ORE, "auto_iron", false);
                next += GAP;
            }

            if (RTSBuildings.isCompleted("mine_2")) {
                drawTaskButton(graphics, mc, mouse, x, next, Items.GOLD_ORE, "auto_gold", false);
                next += GAP;
            }

            if (RTSBuildings.isCompleted("mine_3")) {
                drawTaskButton(graphics, mc, mouse, x, next, Items.DIAMOND, "auto_diamond", false);
            }
        }
    }

    private static boolean hasSelectedWarrior() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null || RTSClientState.selectedVillagers.isEmpty()) {
            return false;
        }

        for (LivingEntity unit : RTSCameraUtil.getAllUnits(mc.level)) {
            if (unit instanceof Monster && RTSClientState.selectedVillagers.contains(unit.getUUID())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Bottom-right button that instantly buys the next town hall tier. Hidden
     * when there is no town hall or it is already at the maximum level.
     */
    private static void drawTownHallUpgrade(GuiGraphics graphics, Minecraft mc, int width, int height, Vector2d mouse) {
        int level = RTSClientState.townHallLevel;

        if (level < 1 || level >= 3) {
            return;
        }

        RTSBuilding next = RTSBuildings.get("town_hall_" + (level + 1));

        if (next == null) {
            return;
        }

        int size = 28;
        int x = width - size - 8;
        // Raised above the bottom edge so it never collides with the unit panel.
        int y = height - size - 128;

        RTSClientState.Rect rect = new RTSClientState.Rect(x, y, size, size);
        boolean hovered = rect.contains(mouse.x, mouse.y);
        boolean affordable = RTSBuildClient.canAfford(next);

        drawStyledButton(graphics, x, y, size, size, hovered, false);
        graphics.renderItem(new ItemStack(Items.EMERALD_BLOCK), x + 6, y + 6);
        graphics.renderOutline(x, y, size, size, affordable
                ? lerpColor(0xDD00E676, 0xFFFFD54F, pulse(2.5F))
                : 0xFFFFC107);

        graphics.drawString(mc.font, "ур." + (level + 1), x - 2, y - 12, 0xFFFFFF00);

        RTSBuildClient.buildButtons.add(rect);
        RTSBuildClient.buildActions.add("upgrade_town_hall");

        if (hovered) {
            pendingTooltip = () -> drawBuildTooltip(graphics, mc, x, y, next);
        }
    }

    private static void drawBuildPanel(GuiGraphics graphics, Minecraft mc, int width, Vector2d mouse) {
        int baseX = width - COL_BUILD;
        int baseY = 8 + GAP;
        int cell = BTN + 6;
        int index = 0;

        // Two columns keep the (now much longer) building list compact and on
        // screen even with many house types.
        for (RTSBuilding building : RTSBuildings.BUILDINGS) {
            if (building.upgrade) {
                continue;
            }

            int bx = baseX - (index % 2) * cell;
            int by = baseY + (index / 2) * cell;

            drawBuildButton(graphics, mc, mouse, bx, by, building);
            index++;
        }
    }

    // Upgrade buttons are now nodes inside the profession tree panel.

    private static void drawBuildButton(GuiGraphics graphics, Minecraft mc, Vector2d mouse, int x, int y, RTSBuilding building) {
        RTSClientState.Rect rect = new RTSClientState.Rect(x, y, BTN, BTN);
        boolean hovered = rect.contains(mouse.x, mouse.y);

        Item icon = building.unlocked ? building.icon : Items.BARRIER;
        boolean affordable = building.unlocked && RTSBuildClient.canAfford(building);

        graphics.fill(x - 1, y - 1, x + BTN + 1, y + BTN + 1, 0xFF000000);

        if (building.unlocked) {
            fillGradientV(graphics, x, y, x + BTN, y + BTN,
                    hovered ? 0xFF27512C : 0xFF23272A, hovered ? 0xFF16301A : 0xFF14181A);
        } else {
            graphics.fill(x, y, x + BTN, y + BTN, 0xFF1E1E1E);
        }
        graphics.renderItem(new ItemStack(icon), x + 6, y + 6);
        graphics.renderOutline(x, y, BTN, BTN, affordable ? 0xFF00E676 : 0xFF555555);

        RTSBuildClient.buildButtons.add(rect);
        RTSBuildClient.buildActions.add("build:" + building.id);

        if (hovered) {
            pendingTooltip = () -> drawBuildTooltip(graphics, mc, x, y, building);
        }
    }

    private static void drawBuildTooltip(GuiGraphics graphics, Minecraft mc, int btnX, int btnY, RTSBuilding building) {
        int lineCount = 1;

        if (building.costWood > 0) lineCount++;
        if (building.costFood > 0) lineCount++;
        if (building.costOre > 0) lineCount++;

        if (!building.unlocked) lineCount++;

        // Width follows the longest line so large costs are never clipped.
        int textW = mc.font.width(building.name);

        if (building.costWood > 0) {
            textW = Math.max(textW, mc.font.width(building.costWood + " / " + RTSClientState.wood) + 30);
        }
        if (building.costFood > 0) {
            textW = Math.max(textW, mc.font.width(building.costFood + " / " + RTSClientState.food) + 30);
        }
        if (building.costOre > 0) {
            textW = Math.max(textW, mc.font.width(building.costOre + " / " + RTSClientState.ore) + 30);
        }

        int tooltipW = Math.max(82, textW + 12);
        int tooltipH = 8 + lineCount * 14;
        int tx = btnX - tooltipW - 8;
        int ty = btnY;

        graphics.fill(tx - 1, ty - 1, tx + tooltipW + 1, ty + tooltipH + 1, 0xFF000000);
        graphics.fill(tx, ty, tx + tooltipW, ty + tooltipH, 0xEE1A1A1A);
        graphics.renderOutline(tx, ty, tooltipW, tooltipH, building.unlocked ? 0xFF00E676 : 0xFF555555);

        graphics.drawString(mc.font, building.name, tx + 6, ty + 4, building.unlocked ? 0xFFFFFF : 0xFF888888);

        int iy = ty + 16;

        if (building.costWood > 0) {
            graphics.renderItem(new ItemStack(Items.OAK_LOG), tx + 4, iy - 2);
            int color = RTSClientState.wood >= building.costWood ? 0xFF00E676 : 0xFFFF5252;
            graphics.drawString(mc.font, building.costWood + " / " + RTSClientState.wood, tx + 24, iy + 2, color);
            iy += 14;
        }

        if (building.costFood > 0) {
            graphics.renderItem(new ItemStack(Items.BREAD), tx + 4, iy - 2);
            int color = RTSClientState.food >= building.costFood ? 0xFF00E676 : 0xFFFF5252;
            graphics.drawString(mc.font, building.costFood + " / " + RTSClientState.food, tx + 24, iy + 2, color);
            iy += 14;
        }

        if (building.costOre > 0) {
            graphics.renderItem(new ItemStack(Items.IRON_INGOT), tx + 4, iy - 2);
            int color = RTSClientState.ore >= building.costOre ? 0xFF00E676 : 0xFFFF5252;
            graphics.drawString(mc.font, building.costOre + " / " + RTSClientState.ore, tx + 24, iy + 2, color);
            iy += 14;
        }

        if (!building.unlocked) {
            graphics.drawString(mc.font, "Закрыто", tx + 6, iy, 0xFF888888);
        }
    }

    private static void drawTaskButton(GuiGraphics graphics, Minecraft mc, Vector2d mouse, int x, int y, Item item, String action, boolean active) {
        RTSClientState.Rect rect = new RTSClientState.Rect(x, y, BTN, BTN);
        boolean hovered = rect.contains(mouse.x, mouse.y);

        drawStyledButton(graphics, x, y, BTN, BTN, hovered, active);
        graphics.renderItem(new ItemStack(item), x + 6, y + 6);

        RTSTaskClient.taskButtons.add(rect);
        RTSTaskClient.taskActions.add(action);

        if (hovered) {
            pendingTooltip = () -> drawLabelTooltip(graphics, mc, x, y, getTaskLabel(action));
        }
    }

    private static void drawLabelTooltip(GuiGraphics graphics, Minecraft mc, int btnX, int btnY, String label) {
        int tw = mc.font.width(label) + 10;
        int tx = btnX - tw - 6;
        int ty = btnY + 6;

        graphics.fill(tx - 1, ty - 1, tx + tw + 1, ty + 15, 0xFF000000);
        fillGradientV(graphics, tx, ty, tx + tw, ty + 14, 0xF01B2A1E, 0xF00A0F0A);
        graphics.renderOutline(tx, ty, tw, 14, lerpColor(0xDD00E676, 0xFF7CFFB2, pulse(2.5F)));
        graphics.drawString(mc.font, label, tx + 5, ty + 3, 0xFFE8F5E9);
    }

    private static String getTaskLabel(String action) {
        if (action.equals("toggle_task")) return "Задачи";
        if (action.equals("mine_menu")) return "Добывать руду";
        if (action.equals("auto_wood")) return "Добывать дерево";
        if (action.equals("build_task")) return "Строить";
        if (action.equals("auto_hunt")) return "Охотиться";
        if (action.equals("defend")) return "Защита";
        if (action.equals("auto_cobble")) return "Булыжник";
        if (action.equals("auto_coal")) return "Уголь";
        if (action.equals("auto_iron")) return "Железо";
        if (action.equals("auto_gold")) return "Золото";
        if (action.equals("auto_diamond")) return "Алмазы";
        return "";
    }

    private static void drawSelectionPanel(GuiGraphics graphics, Minecraft mc, int width, int height, Vector2d mouse) {
        List<LivingEntity> all = RTSCameraUtil.getAllUnits(mc.level);
        List<LivingEntity> valid = new ArrayList<>();

        for (UUID id : RTSClientState.selectedVillagers) {
            for (LivingEntity unit : all) {
                if (unit.getUUID().equals(id) && unit.isAlive()) {
                    valid.add(unit);
                    break;
                }
            }
        }

        List<UUID> validIds = new ArrayList<>();

        for (LivingEntity unit : valid) {
            validIds.add(unit.getUUID());
        }

        RTSClientState.selectedVillagers.clear();
        RTSClientState.selectedVillagers.addAll(validIds);

        if (valid.isEmpty()) {
            return;
        }

        if (valid.size() == 1) {
            drawSingleUnitPanel(graphics, mc, width, height, valid.get(0));
        } else {
            drawMultiUnitPanel(graphics, mc, width, height, valid, mouse);
        }
    }

    private static void drawSingleUnitPanel(GuiGraphics graphics, Minecraft mc, int width, int height, LivingEntity villager) {
        int w = 216;
        int h = 72;
        int x = 96;
        int y = height - h - 10;

        if (x + w > width - 4) {
            x = width - w - 4;
        }

        if (x < 4) {
            x = 4;
        }

        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        graphics.fill(x, y, x + w, y + h, 0xDD141414);
        graphics.fill(x, y, x + 3, y + h, 0xFF00E676);

        int px = x + 10;
        int py = y + 8;
        int ps = 56;

        fillGradientV(graphics, px, py, px + ps, py + ps, 0xF0232824, 0xF0121512);
        graphics.renderOutline(px, py, ps, ps, lerpColor(0xCC00E676, 0xFF7CFFB2, pulse(1.8F)));

        // Waist-up portrait, but noticeably bigger: the feet sit well below the
        // box, so the scissor clip shows head + upper body, facing the viewer.
        drawUnitModel(graphics, villager, px + ps / 2, py + 114, 54, px, py, px + ps, py + ps);

        String name = trim(villager.getDisplayName().getString(), 18);
        String profession = trim(getProfessionName(villager), 15) + "  ур." + RTSProfClient.getLevel(villager.getUUID());

        graphics.drawString(mc.font, name, x + 76, y + 10, 0xFFFFFFFF);
        graphics.drawString(mc.font, profession, x + 76, y + 22, 0xFF9CCC65);

        int hx = x + 76;
        int hy = y + 40;
        int hw = 130;
        int hh = 8;

        float ratio = getHealthRatio(villager);

        graphics.fill(hx, hy, hx + hw, hy + hh, 0xFF3B0000);
        graphics.fill(hx, hy, hx + (int) (hw * ratio), hy + hh, getHealthColor(ratio));
        graphics.renderOutline(hx, hy, hw, hh, 0xFF000000);
    }

    private static void drawMultiUnitPanel(GuiGraphics graphics, Minecraft mc, int width, int height, List<LivingEntity> villagers, Vector2d mouse) {
        int maxIcons = 6;
        int shown = Math.min(villagers.size(), maxIcons);
        boolean overflow = villagers.size() > maxIcons;

        int slot = 36;
        int icon = 32;
        int w = 12 + (shown + (overflow ? 1 : 0)) * slot;
        int h = 58;

        int x = 96;
        int y = height - h - 10;

        if (x + w > width - 4) {
            x = width - w - 4;
        }

        if (x < 4) {
            x = 4;
        }

        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        graphics.fill(x, y, x + w, y + h, 0xDD141414);
        graphics.fill(x, y, x + w, y + 2, 0xFF00E676);

        graphics.drawString(mc.font, String.valueOf(villagers.size()), x + 6, y + 5, 0xFFFFFF);

        int ix = x + 6;
        int iy = y + 18;

        for (int i = 0; i < shown; i++) {
            LivingEntity villager = villagers.get(i);

            drawUnitIcon(graphics, mc, villager, ix, iy, mouse);

            RTSClientState.unitButtons.add(new RTSClientState.Rect(ix, iy, icon, icon + 6));
            RTSClientState.unitIds.add(villager.getUUID());

            ix += slot;
        }

        if (overflow) {
            graphics.fill(ix, iy, ix + icon, iy + icon + 6, 0xFF2B2B2B);
            graphics.renderOutline(ix, iy, icon, icon + 6, 0xFF00E676);

            String text = "+" + (villagers.size() - maxIcons);

            graphics.drawString(mc.font, text, ix + 16 - mc.font.width(text) / 2, iy + 13, 0xFFFFFF);
        }
    }

    /**
     * Renders the real entity model (villager or warrior) into the HUD, live:
     * the body is turned to face the viewer while the actual head pitch is kept,
     * so a villager looking up/down is reflected in the panel.
     */
    private static void drawUnitModel(GuiGraphics graphics, LivingEntity unit, int centerX, int anchorY, int scale,
                                      int clipX1, int clipY1, int clipX2, int clipY2) {
        float oldYaw = unit.getYRot();
        float oldYawO = unit.yRotO;
        float oldPitch = unit.getXRot();
        float oldPitchO = unit.xRotO;
        float oldBodyYaw = unit.yBodyRot;
        float oldBodyYawO = unit.yBodyRotO;
        float oldHeadYaw = unit.yHeadRot;
        float oldHeadYawO = unit.yHeadRotO;

        // Face the viewer. All "old" fields are set too, otherwise the renderer
        // interpolates from the previous rotation and the model faces away.
        unit.setYRot(0.0F);
        unit.yRotO = 0.0F;
        unit.setXRot(0.0F);
        unit.xRotO = 0.0F;
        unit.yBodyRot = 0.0F;
        unit.yBodyRotO = 0.0F;
        unit.yHeadRot = 0.0F;
        unit.yHeadRotO = 0.0F;

        // Clip to the portrait so the zoomed-in (waist-up) model stays in its box.
        graphics.enableScissor(clipX1, clipY1, clipX2, clipY2);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, anchorY, 50.0F);
        pose.scale((float) -scale, (float) scale, (float) scale);
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        dispatcher.render(unit, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, pose, buffers, 0xF000F0);
        buffers.endBatch();

        dispatcher.setRenderShadow(true);
        pose.popPose();

        graphics.disableScissor();

        unit.setYRot(oldYaw);
        unit.yRotO = oldYawO;
        unit.setXRot(oldPitch);
        unit.xRotO = oldPitchO;
        unit.yBodyRot = oldBodyYaw;
        unit.yBodyRotO = oldBodyYawO;
        unit.yHeadRot = oldHeadYaw;
        unit.yHeadRotO = oldHeadYawO;
    }

    private static void drawUnitIcon(GuiGraphics graphics, Minecraft mc, LivingEntity villager, int x, int y, Vector2d mouse) {
        boolean hovered = mouse.x >= x && mouse.x <= x + 32 && mouse.y >= y && mouse.y <= y + 38;

        fillGradientV(graphics, x, y, x + 32, y + 32,
                hovered ? 0xFF3A4038 : 0xFF232823, hovered ? 0xFF20261F : 0xFF121511);
        drawUnitModel(graphics, villager, x + 16, y + 44, 22, x, y, x + 32, y + 32);

        float ratio = getHealthRatio(villager);

        graphics.fill(x + 2, y + 34, x + 30, y + 38, 0xFF3B0000);
        graphics.fill(x + 2, y + 34, x + 2 + (int) (28 * ratio), y + 38, getHealthColor(ratio));
        graphics.renderOutline(x, y, 32, 38, hovered ? 0xFFFFFF00 : 0xFF00E676);
    }

    private static void drawCentered(GuiGraphics graphics, Minecraft mc, String text, int centerX, int y, int color) {
        graphics.drawString(mc.font, text, centerX - mc.font.width(text) / 2, y, color);
    }

    private static float getHealthRatio(LivingEntity villager) {
        return villager.getMaxHealth() <= 0.0F ? 0.0F : villager.getHealth() / villager.getMaxHealth();
    }

    private static int getHealthColor(float ratio) {
        if (ratio > 0.6F) {
            return 0xFF00E676;
        }

        if (ratio > 0.3F) {
            return 0xFFFFC107;
        }

        return 0xFFFF5252;
    }

    private static String trim(String text, int max) {
        if (text.length() <= max) {
            return text;
        }

        return text.substring(0, max - 1) + ".";
    }

    // === Стиль: градиенты, анимации, панели ===

    /** Vertical gradient fill (top -> bottom). */
    private static void fillGradientV(GuiGraphics graphics, int x1, int y1, int x2, int y2, int top, int bottom) {
        int h = Math.max(1, y2 - y1);

        for (int i = 0; i < h; i++) {
            float t = h <= 1 ? 0.0F : i / (float) (h - 1);
            graphics.fill(x1, y1 + i, x2, y1 + i + 1, lerpColor(top, bottom, t));
        }
    }

    private static int lerpColor(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;

        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);

        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    /** Smooth 0..1 oscillation used for glow / hover animations. */
    private static float pulse(float speed) {
        double seconds = (System.currentTimeMillis() % 60000L) / 1000.0;
        return (float) (0.5 + 0.5 * Math.sin(seconds * speed));
    }

    /** Panel background: black frame, vertical gradient and a glowing border. */
    private static void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF000000);
        fillGradientV(graphics, x, y, x + w, y + h, 0xF01B2A1E, 0xF00A0F0A);
        graphics.renderOutline(x, y, w, h, lerpColor(0xCC00E676, 0xFF7CFFB2, pulse(2.0F)));
    }

    /** Styled button: gradient fill plus an animated glow when hovered/active. */
    private static void drawStyledButton(GuiGraphics graphics, int x, int y, int w, int h, boolean hovered, boolean active) {
        int top = active ? 0xFF2E7D32 : hovered ? 0xFF27512C : 0xFF23272A;
        int bottom = active ? 0xFF17421C : hovered ? 0xFF16301A : 0xFF14181A;

        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        fillGradientV(graphics, x, y, x + w, y + h, top, bottom);

        int target = active ? 0xFFFFC107 : 0xFF7CFFB2;
        graphics.renderOutline(x, y, w, h,
                lerpColor(0xDD00E676, target, hovered || active ? pulse(4.0F) : 0.35F));
    }

    private static Item getProfessionIcon(LivingEntity unit) {
        // Combatants are raiders, not villagers.
        if (!(unit instanceof Villager villager)) {
            if (unit instanceof Pillager) {
                return Items.CROSSBOW;
            }

            if (unit instanceof Evoker) {
                return Items.TOTEM_OF_UNDYING;
            }

            return Items.IRON_SWORD;
        }

        int prof = RTSProfClient.get(villager.getUUID());

        if (prof == 1) return Items.STONE_PICKAXE;
        if (prof == 2) return Items.STONE_AXE;
        if (prof == 3) return Items.WHEAT;
        if (prof == 4) return Items.IRON_SWORD;

        VillagerProfession profession = villager.getVillagerData().getProfession();

        if (profession == VillagerProfession.FARMER) return Items.WHEAT;
        if (profession == VillagerProfession.LIBRARIAN) return Items.BOOK;
        if (profession == VillagerProfession.ARMORER) return Items.IRON_CHESTPLATE;
        if (profession == VillagerProfession.WEAPONSMITH) return Items.IRON_SWORD;
        if (profession == VillagerProfession.TOOLSMITH) return Items.IRON_PICKAXE;
        if (profession == VillagerProfession.CLERIC) return Items.BLAZE_ROD;
        if (profession == VillagerProfession.BUTCHER) return Items.BEEF;
        if (profession == VillagerProfession.LEATHERWORKER) return Items.LEATHER;
        if (profession == VillagerProfession.MASON) return Items.BRICK;
        if (profession == VillagerProfession.SHEPHERD) return Items.SHEARS;
        if (profession == VillagerProfession.CARTOGRAPHER) return Items.MAP;
        if (profession == VillagerProfession.NITWIT) return Items.BREAD;

        return Items.EMERALD;
    }

    private static String getProfessionName(LivingEntity unit) {
        if (!(unit instanceof Villager villager)) {
            if (unit instanceof Pillager) {
                return "Арбалетчик";
            }

            if (unit instanceof Evoker) {
                return "Призыватель";
            }

            return "Воин";
        }

        int prof = RTSProfClient.get(villager.getUUID());

        if (prof == 1) return "Шахтер";
        if (prof == 2) return "Лесоруб";
        if (prof == 3) return "Фермер";
        if (prof == 4) return "Воин";

        ResourceLocation key = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(villager.getVillagerData().getProfession());

        if (key == null) {
            return "Без профессии";
        }

        String path = key.getPath();

        if (path.equals("none")) {
            return "Без профессии";
        }

        return Component.translatable("entity.minecraft.villager." + path).getString();
    }
}