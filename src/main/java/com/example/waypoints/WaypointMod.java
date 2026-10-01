package com.example.waypoints;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

@Mod(modid = WaypointMod.MODID, name = "Waypoints", version = "1.0", clientSideOnly = true)
public class WaypointMod {
    public static final String MODID = "waypoints";

    private static final int SLOTS = 5;
    // 1 синий, 2 розовый, 3 красный, 4 зелёный, 5 оранжевый
    private static final int[] COLORS = {0x3366FF, 0xFF69B4, 0xFF3333, 0x33FF33, 0xFF9900};
    private static final String[] COLOR_NAMES = {"синяя", "розовая", "красная", "зелёная", "оранжевая"};

    // Дальше этого расстояния подпись "прижимается" к экрану, но остаётся видимой
    private static final double MAX_LABEL_DIST = 30.0D;
    // Размер подписи (больше число = крупнее текст)
    private static final double LABEL_SCALE = 0.0045D;

    private static class Waypoint {
        final int x, y, z;
        String name;

        Waypoint(int x, int y, int z, String name) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.name = name;
        }
    }

    private final Waypoint[] slots = new Waypoint[SLOTS];
    // Порядок установки: последний элемент = последняя поставленная метка
    private final List<Integer> order = new ArrayList<Integer>();

    private KeyBinding keySet;
    private KeyBinding keyRemove;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        keySet = new KeyBinding("Поставить метку", Keyboard.KEY_Z, "Тактические метки");
        keyRemove = new KeyBinding("Удалить последнюю метку", Keyboard.KEY_X, "Тактические метки");
        ClientRegistry.registerKeyBinding(keySet);
        ClientRegistry.registerKeyBinding(keyRemove);

        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);

        ClientCommandHandler.instance.registerCommand(new WpCommand());
    }

    // ---------- Логика меток ----------

    private void msg(String text) {
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p != null) {
            p.addChatMessage(new ChatComponentText("[Метки] " + text));
        }
    }

    private int firstFreeSlot() {
        for (int i = 0; i < SLOTS; i++) {
            if (slots[i] == null) return i;
        }
        return -1;
    }

    private void addWaypoint(EntityPlayerSP player) {
        int slot = firstFreeSlot();
        if (slot < 0) {
            msg("Все 5 слотов заняты. Нажмите X, чтобы удалить последнюю метку.");
            return;
        }
        int x = MathHelper.floor_double(player.posX);
        int y = MathHelper.floor_double(player.posY);
        int z = MathHelper.floor_double(player.posZ);
        slots[slot] = new Waypoint(x, y, z, "Метка " + (slot + 1));
        order.add(slot);
        msg("Поставлена " + COLOR_NAMES[slot] + " метка " + (slot + 1) + ": " + x + " " + y + " " + z);
    }

    private void removeLast() {
        if (order.isEmpty()) {
            msg("Нет меток для удаления.");
            return;
        }
        int slot = order.remove(order.size() - 1);
        slots[slot] = null;
        msg("Удалена метка " + (slot + 1) + ".");
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        while (keySet.isPressed()) {
            addWaypoint(mc.thePlayer);
        }
        while (keyRemove.isPressed()) {
            removeLast();
        }
    }

    // ---------- Отрисовка ----------

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        RenderManager rm = mc.getRenderManager();
        double px = rm.viewerPosX;
        double py = rm.viewerPosY;
        double pz = rm.viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        // Лучи
        for (int i = 0; i < SLOTS; i++) {
            Waypoint wp = slots[i];
            if (wp == null) continue;
            drawBeam(wp.x + 0.5D - px, wp.y - py, wp.z + 0.5D - pz, COLORS[i]);
        }

        GlStateManager.enableTexture2D();

        // Подписи
        for (int i = 0; i < SLOTS; i++) {
            Waypoint wp = slots[i];
            if (wp == null) continue;
            drawLabel(mc, rm, wp, COLORS[i], px, py, pz);
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private void drawLabel(Minecraft mc, RenderManager rm, Waypoint wp, int rgb,
                           double px, double py, double pz) {
        // Расстояние до самой метки в метрах (блоках)
        double ddx = wp.x + 0.5D - px;
        double ddy = wp.y - py;
        double ddz = wp.z + 0.5D - pz;
        double dist = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);

        // Позиция подписи: над меткой, но не дальше MAX_LABEL_DIST
        double lx = ddx;
        double ly = ddy + 2.0D;
        double lz = ddz;
        double ldist = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (ldist < 0.001D) ldist = 0.001D;
        double drawDist = Math.min(ldist, MAX_LABEL_DIST);
        double k = drawDist / ldist;
        lx *= k;
        ly *= k;
        lz *= k;

        float scale = (float) (LABEL_SCALE * Math.max(drawDist, 2.0D));
        FontRenderer fr = mc.fontRendererObj;
        int color = 0xFF000000 | rgb;
        String name = wp.name;
        String meters = Math.round(dist) + " м";

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) lx, (float) ly, (float) lz);
        GlStateManager.rotate(-rm.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(rm.playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-scale, -scale, scale);

        // Цветной квадратик над текстом
        GlStateManager.disableTexture2D();
        float r = ((rgb >> 16) & 255) / 255.0F;
        float g = ((rgb >> 8) & 255) / 255.0F;
        float b = (rgb & 255) / 255.0F;
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(-4.0D, -14.0D, 0.0D).color(r, g, b, 1.0F).endVertex();
        wr.pos(-4.0D, -6.0D, 0.0D).color(r, g, b, 1.0F).endVertex();
        wr.pos(4.0D, -6.0D, 0.0D).color(r, g, b, 1.0F).endVertex();
        wr.pos(4.0D, -14.0D, 0.0D).color(r, g, b, 1.0F).endVertex();
        t.draw();
        GlStateManager.enableTexture2D();

        fr.drawStringWithShadow(name, -fr.getStringWidth(name) / 2.0F, 0.0F, color);
        fr.drawStringWithShadow(meters, -fr.getStringWidth(meters) / 2.0F, 10.0F, 0xFFFFFFFF);

        GlStateManager.popMatrix();
    }

    private void drawBeam(double x, double y, double z, int rgb) {
        float r = ((rgb >> 16) & 255) / 255.0F;
        float g = ((rgb >> 8) & 255) / 255.0F;
        float b = (rgb & 255) / 255.0F;
        float a = 0.4F;
        double w = 0.2D;
        double h = 300.0D;

        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        side(wr, x - w, z - w, x + w, z - w, y, h, r, g, b, a);
        side(wr, x + w, z - w, x + w, z + w, y, h, r, g, b, a);
        side(wr, x + w, z + w, x - w, z + w, y, h, r, g, b, a);
        side(wr, x - w, z + w, x - w, z - w, y, h, r, g, b, a);
        t.draw();
    }

    private void side(WorldRenderer wr, double x1, double z1, double x2, double z2,
                      double y, double h, float r, float g, float b, float a) {
        wr.pos(x1, y, z1).color(r, g, b, a).endVertex();
        wr.pos(x2, y, z2).color(r, g, b, a).endVertex();
        wr.pos(x2, y + h, z2).color(r, g, b, a).endVertex();
        wr.pos(x1, y + h, z1).color(r, g, b, a).endVertex();
    }

    // ---------- Команда /wp ----------

    private class WpCommand extends CommandBase {
        public String getCommandName() {
            return "wp";
        }

        public String getCommandUsage(ICommandSender sender) {
            return "/wp rename <1-5> <название>  |  /wp clear";
        }

        public int getRequiredPermissionLevel() {
            return 0;
        }

        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
            if (args.length >= 1 && args[0].equalsIgnoreCase("clear")) {
                for (int i = 0; i < SLOTS; i++) slots[i] = null;
                order.clear();
                msg("Все метки удалены.");
                return;
            }
            if (args.length >= 3 && args[0].equalsIgnoreCase("rename")) {
                int n;
                try {
                    n = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    msg(getCommandUsage(sender));
                    return;
                }
                if (n < 1 || n > SLOTS || slots[n - 1] == null) {
                    msg("Метки с таким номером нет.");
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    if (i > 2) sb.append(' ');
                    sb.append(args[i]);
                }
                slots[n - 1].name = sb.toString();
                msg("Метка " + n + " теперь называется: " + sb);
                return;
            }
            msg(getCommandUsage(sender));
        }
    }
}
