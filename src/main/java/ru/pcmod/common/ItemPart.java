package ru.pcmod.common;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class ItemPart extends Item {
    public final PartSpec spec;

    public ItemPart(Item.Properties props, PartSpec spec) {
        super(props);
        this.spec = spec;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tips, TooltipFlag flag) {
        List<String> list = new ArrayList<String>();
        String g = "\u00a77";
        switch (spec.cat) {
            case MOTHERBOARD:
                list.add(g + "Socket " + PartSpec.socketName(spec.socket));
                list.add(g + spec.ramSlots + " x " + PartSpec.ramName(spec.ramType) + " slots");
                break;
            case CPU:
                list.add(g + "Socket " + PartSpec.socketName(spec.socket));
                list.add(g + spec.cores + " cores @ " + spec.ghz + " GHz");
                list.add(g + "TDP " + spec.watts + " W");
                break;
            case COOLER:
                list.add(g + "Cooling rating " + spec.cooling);
                break;
            case RAM:
                list.add(g + PartSpec.ramName(spec.ramType) + ", " + spec.memMB + " MB");
                break;
            case GPU:
                list.add(g + "VRAM " + spec.vram + " MB");
                list.add(g + "Draw " + spec.watts + " W");
                if (spec.hash > 0) list.add("\u00a76Mining: " + spec.hash + " MH/s (miner farm)");
                break;
            case STORAGE:
                list.add(g + "Capacity " + spec.storageKB + " KB");
                list.add(g + "Speed x" + spec.speed);
                if (spec.hdd) {
                    list.add("\u00a73Server drive: +" + spec.storageKB / 8 + " virus mail slots");
                    list.add("\u00a7cHeats the server: +" + TileServer.DRIVE_HEAT_W + " W per drive");
                }
                if (VFS.hasOS(stack)) {
                    list.add("\u00a7a" + VFS.osName(VFS.root(stack)) + " installed");
                }
                break;
            case PSU:
                list.add(g + "Output " + spec.watts + " W");
                break;
            case MEDIA:
                if (spec.os.equals("pixel")) {
                    list.add(g + "Bootable PixelOS installer");
                    list.add("\u00a7ePixel-art desktop. Needs a powerful PC:");
                    list.add("\u00a7e6+ cores, 8 GB RAM, GT-700+ GPU, 128 KB drive");
                } else {
                    list.add(g + "Bootable PCOS installer");
                }
                break;
            case NET:
                list.add(g + "Wi-Fi adapter: connects the PC to a router wirelessly");
                break;
            case DATA: {
                List<Virus> vs = VirusDisk.read(stack);
                list.add(g + "Data disk: carries viruses between PCs");
                list.add("\u00a75Viruses: " + vs.size() + " / " + VirusDisk.CAPACITY);
                for (int i = 0; i < vs.size() && i < 4; i++) list.add(g + " " + vs.get(i).shortLine());
                if (vs.size() > 4) list.add(g + " ... +" + (vs.size() - 4) + " more");
                list.add(g + "Insert in the 'Data' slot, then use 'virgen save/load'");
                break;
            }
            default:
                break;
        }
        for (String l : list) tips.add(Component.literal(l));
    }
}
