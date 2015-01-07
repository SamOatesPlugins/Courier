package se.troed.plugin.Courier;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.*;

import java.util.logging.Level;

public class LetterRenderer extends MapRenderer {

    @SuppressWarnings("FieldCanBeLocal")
    private final int HEADER_POS = 2; // 2*getHeight()
    @SuppressWarnings("FieldCanBeLocal")
    private final int BODY_POS = 4; // 4*getHeight()
    private final Courier plugin;
    @SuppressWarnings("FieldCanBeLocal")
    private final int CANVAS_WIDTH = 128;
    @SuppressWarnings("FieldCanBeLocal")
    private final int CANVAS_HEIGHT = 128;
//    private final byte[] clearImage = new byte[128*128];  // nice letter background image todo
//    private int lastId = -1;
//    private boolean clear = false;
    private String cachedPrivacy;
    private String cachedReceiver = "";

    public LetterRenderer(Courier p) {
        super(true); // all our messages are contextual (i.e different for different players)
        plugin = p;
    }

    // This method gets called at 20tps whenever a map is in a players inventory. Bail out as quickly as possible if we
    // shouldn't do anything with it.
    // https://bukkit.atlassian.net/browse/BUKKIT-476
    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        Letter letter = null;
        ItemStack item = player.getItemInHand();
        if(plugin.courierMapType(item) != Courier.NONE) {
            letter = plugin.getTracker().getLetter(item);
            if(letter == null) { // plugin.courierMapType(item) != Courier.LETTER
                // parchment - drawn blank below
                // currently redraws at 20 tps - no letter to clear dirty flag
//                plugin.getCConfig().clog(Level.FINE, "Rendering a Courier Parchment on Map (" + map.getId() + ")");
                for(int j = 0; j < CANVAS_HEIGHT; j++) {
                    for(int i = 0; i < CANVAS_WIDTH; i++) {
                        //                    canvas.setPixel(i, j, clearImage[j*128+i]);
                        canvas.setPixel(i, j, MapPalette.TRANSPARENT);
                    }
                }
            } else if(letter.getDirty()) {
                plugin.getCConfig().clog(Level.FINE, "Rendering a Courier Letter (" + letter.getId() + ") on Map (" + map.getId() + ")");
                for(int j = 0; j < CANVAS_HEIGHT; j++) {
                    for(int i = 0; i < CANVAS_WIDTH; i++) {
                        //                    canvas.setPixel(i, j, clearImage[j*128+i]);
                        canvas.setPixel(i, j, MapPalette.TRANSPARENT);
                    }
                }
                // todo: idea for pvp war servers: "your mail has fallen into enemy hands". "they've read it!")
                if(letter.isAllowedToSee(player)) {
                    int drawPos = HEADER_POS;
                    if(letter.getHeader() != null) {
                        canvas.drawText(0, MinecraftFont.Font.getHeight() * drawPos, MinecraftFont.Font, letter.getHeader());
                        drawLine(canvas, 10, MinecraftFont.Font.getHeight() * (drawPos+1) +
                                            (int)(MinecraftFont.Font.getHeight() * 0.4), CANVAS_WIDTH-11, MapPalette.DARK_BROWN);
                        drawPos = BODY_POS;
                    }

                    canvas.drawText(letter.getLeftMarkerPos(), MinecraftFont.Font.getHeight(), MinecraftFont.Font, letter.getLeftMarker());
                    canvas.drawText(letter.getRightMarkerPos(), MinecraftFont.Font.getHeight(), MinecraftFont.Font, letter.getRightMarker());

                    canvas.drawText(0,
                                    MinecraftFont.Font.getHeight() * drawPos,
                                    MinecraftFont.Font, Letter.MESSAGE_COLOR + letter.getMessage());

                    if(letter.getDisplayDate() != null) {
                        canvas.drawText(letter.getDisplayDatePos(),
                                        0,
                                        MinecraftFont.Font, Letter.DATE_COLOR + letter.getDisplayDate());
                    }

                    // this is the actual time we can be sure a letter has been read
                    // post an event to make sure we don't block the rendering pipeline
                    if(!letter.getRead()) {
                        CourierReadEvent event = new CourierReadEvent(player, letter.getId());
                        plugin.getServer().getPluginManager().callEvent(event);
                        letter.setRead(true);
                    }
                } else {
                    if(!letter.getReceiver().equalsIgnoreCase(cachedReceiver)) {
                        cachedReceiver = letter.getReceiver();
                        cachedPrivacy = plugin.getCConfig().getPrivacyLocked(cachedReceiver);
                    }
                    canvas.drawText(0, MinecraftFont.Font.getHeight()*HEADER_POS, MinecraftFont.Font, cachedPrivacy);
                }
                letter.setDirty(false);
                player.sendMap(map);
            }
        }
    }

    // horisontal lines
    static void drawLine(MapCanvas canvas, int x, int y, int x2, byte c) {
        for(int t = x; t <= x2; t++) {
            canvas.setPixel(t, y, c);
        }
    }
}
