package com.kuilunfuzhe.monvhua.features.forensics.cards;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.item.Item;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import java.util.*;

/** Uses only the frozen public item projection, never the live inventory or hidden audit. */
final class ClueCardItemPreview {
    private record Cached(String source,ItemStack stack){}
    private static final Map<String,Cached> CACHE=new LinkedHashMap<>(16,.75f,true);
    static void clear(){CACHE.clear();}
    static ItemStack stack(ClueCardPackets.Detail detail){
        if(detail==null||detail.item()==null||detail.item().isEmpty())return ItemStack.EMPTY;
        var client=MinecraftClient.getInstance();if(client.world==null)return ItemStack.EMPTY;
        var cached=CACHE.get(detail.id());if(cached!=null&&cached.source().equals(detail.item()))return cached.stack();
        try{
            var n=NbtCompound.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(detail.item())).getOrThrow();
            var stack=ItemStack.CODEC.parse(client.world.getRegistryManager().getOps(NbtOps.INSTANCE),n).getOrThrow();
            CACHE.put(detail.id(),new Cached(detail.item(),stack));while(CACHE.size()>64)CACHE.remove(CACHE.keySet().iterator().next());return stack;
        }catch(RuntimeException error){return ItemStack.EMPTY;}
    }
    static boolean isItem(String id){var detail=ClueCardClient.DETAILS.get(id);var card=ClueCardClient.find(id);return card!=null&&"item".equals(card.kind())||detail!=null&&detail.item()!=null&&!detail.item().isEmpty();}
    static boolean render(DrawContext c,ClueCardPackets.Detail detail,int x,int y,int w,int h){
        if(detail==null||detail.item()==null||detail.item().isEmpty())return false;
        var stack=stack(detail);if(stack.isEmpty())return true;
        c.fill(x,y,x+w,y+h,0xffb6a584);float scale=Math.min(4,Math.min(w,h)/20f);
        c.getMatrices().pushMatrix();c.getMatrices().translate(x+w/2f-8*scale,y+h/2f-8*scale);c.getMatrices().scale(scale,scale);c.drawItem(stack,0,0);c.getMatrices().popMatrix();
        var font=MinecraftClient.getInstance().textRenderer;if(h>=30)c.drawText(font,"×"+stack.getCount(),x+w-font.getWidth("×"+stack.getCount())-4,y+h-12,ClueFolioScreen.INK,false);return true;
    }
    static List<Text> tooltip(String id){
        var client=MinecraftClient.getInstance();var stack=stack(ClueCardClient.DETAILS.get(id));
        if(stack.isEmpty()||client.world==null)return List.of(Text.literal("正在读取物品记录……"));
        var lines=new ArrayList<>(stack.getTooltip(Item.TooltipContext.create(client.world),client.player,TooltipType.BASIC));
        lines.add(Text.literal("记录数量："+stack.getCount()));if(stack.isDamageable())lines.add(Text.literal("耐久："+(stack.getMaxDamage()-stack.getDamage())+" / "+stack.getMaxDamage()));return lines;
    }
    static List<net.minecraft.text.OrderedText> lines(String id,int width){
        var font=MinecraftClient.getInstance().textRenderer;var out=new ArrayList<net.minecraft.text.OrderedText>();
        for(var text:tooltip(id))out.addAll(font.wrapLines(text,width));return out;
    }
    static void hover(DrawContext c,String id,int mx,int my,int x,int y,int w,int h){
        var stack=stack(ClueCardClient.DETAILS.get(id));if(!stack.isEmpty()&&mx>=x&&mx<x+w&&my>=y&&my<y+h)c.drawItemTooltip(MinecraftClient.getInstance().textRenderer,stack,mx,my);
    }
    static void open(net.minecraft.client.gui.screen.Screen parent,String id){MinecraftClient.getInstance().setScreen(new ClueCardItemScreen(parent,id));}
}
