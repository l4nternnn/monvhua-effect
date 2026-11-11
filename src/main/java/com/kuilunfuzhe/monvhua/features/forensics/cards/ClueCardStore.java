package com.kuilunfuzhe.monvhua.features.forensics.cards;

import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import java.util.*;

/** Isolated experimental card ledger. Private audit data never doubles as a network DTO. */
public final class ClueCardStore extends PersistentState {
    public static final Codec<ClueCardStore> CODEC=NbtCompound.CODEC.xmap(ClueCardStore::new,s->s.data.copy());
    public static final PersistentStateType<ClueCardStore> TYPE=new PersistentStateType<>("monvhua_clue_cards",ClueCardStore::new,CODEC,DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final NbtCompound data;
    public record Fields(String title,String time,String place,String event,String inference) {
        public Fields { title=clean(title,32);time=clean(time,32);place=clean(place,48);event=clean(event,256);inference=clean(inference,256); }
        private static String clean(String value,int limit) {
            if(value==null)return "";
            StringBuilder out=new StringBuilder();
            value.codePoints().filter(c->!Character.isISOControl(c)&&c!=0x00a7).limit(limit).forEach(out::appendCodePoint);
            return out.toString().strip();
        }
        public NbtCompound nbt(){var n=new NbtCompound();n.putString("title",title);n.putString("time",time);n.putString("place",place);n.putString("event",event);n.putString("inference",inference);return n;}
        public static Fields read(NbtCompound n){return new Fields(n.getString("title",""),n.getString("time",""),n.getString("place",""),n.getString("event",""),n.getString("inference",""));}
    }
    public record Summary(String id,boolean owned,boolean locked,boolean inBag,int revision,Fields fields,String presenter,boolean collected,String group,String groupLabel){}
    public ClueCardStore(){this(new NbtCompound());}
    private ClueCardStore(NbtCompound n){data=n;int schema=n.getInt("schema",1);if(schema!=1)throw new IllegalStateException("Unknown clue card schema "+schema);data.putInt("schema",1);}
    public static ClueCardStore get(MinecraftServer server){return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);}
    private NbtCompound section(String key){if(!data.contains(key))data.put(key,new NbtCompound());return data.getCompoundOrEmpty(key);}
    private NbtCompound card(String id){return section("cards").getCompoundOrEmpty(id);}
    public String session(){return data.getString("current","");}
    private NbtCompound run(){return section("runs").getCompoundOrEmpty(session());}
    public boolean participant(UUID viewer){return !session().isEmpty()&&run().getCompoundOrEmpty("players").contains(viewer.toString());}
    public boolean recording(UUID viewer){return participant(viewer)&&run().getBoolean("recording",false);}
    public void start(String session,Map<UUID,String> players){
        if(session==null||!session.matches("[a-zA-Z0-9_-]{1,40}")||players.isEmpty()||players.size()>16)throw new IllegalArgumentException("实验编号或参与者无效（最多16人）");
        if(section("runs").contains(session))throw new IllegalArgumentException("实验编号已使用，请使用新编号，避免重复发放");
        var r=new NbtCompound();var members=new NbtCompound();players.forEach((id,name)->members.putString(id.toString(),name));
        r.put("players",members);r.putBoolean("recording",true);r.put("comparisons",new NbtList());section("runs").put(session,r);data.putString("current",session);markDirty();
    }
    public void closeRecording(){run().putBoolean("recording",false);markDirty();}
    public int ownedCount(UUID viewer){int count=0;for(String id:section("cards").getKeys())if(owns(viewer,card(id)))count++;return count;}
    private void requireCapacity(UUID viewer){if(ownedCount(viewer)>=64)throw new IllegalArgumentException("卡袋已满");int total=0;for(String id:section("cards").getKeys())if(session().equals(card(id).getString("session","")))total++;if(total>=256)throw new IllegalArgumentException("本次实验记录数量已达256张上限");}
    private boolean owns(UUID viewer,NbtCompound c){return participant(viewer)&&session().equals(c.getString("session",""))&&viewer.toString().equals(c.getString("owner",""));}
    public boolean owns(UUID viewer,String id){return owns(viewer,card(id));}
    private NbtCompound requireOwned(UUID viewer,String id){var c=card(id);if(!owns(viewer,c))throw new IllegalArgumentException("无权操作该卡片");return c;}
    public boolean readable(UUID viewer,String id){var c=card(id);return participant(viewer)&&session().equals(c.getString("session",""))&&(owns(viewer,c)||c.getBoolean("public",false)||c.getCompoundOrEmpty("readers").getBoolean(viewer.toString(),false));}
    public String create(UUID owner,Fields fields,NbtCompound snapshot,NbtCompound audit){
        if(!recording(owner))throw new IllegalArgumentException("当前未开放记录");
        requireCapacity(owner);
        String id=UUID.randomUUID().toString();var c=new NbtCompound();c.putString("owner",owner.toString());c.putString("session",session());
        c.putString("root",id);c.putString("snapshot",id);c.put("fields",fields.nbt());c.put("audit",audit.copy());c.putInt("revision",0);c.putBoolean("in_bag",true);
        section("snapshots").put(id,snapshot.copy());section("cards").put(id,c);markDirty();return id;
    }
    public String importWound(UUID viewer,String source,String group,String label,Fields fields,NbtCompound snapshot){
        for(String id:section("cards").getKeys())if(owns(viewer,card(id))&&source.equals(card(id).getString("wound_source","")))return id;
        var audit=new NbtCompound();audit.putString("wound_source",source);String id=create(viewer,fields,snapshot,audit);var c=card(id);c.putString("wound_source",source);c.putString("group",group);c.putString("group_label",label);markDirty();return id;
    }
    public void edit(UUID viewer,String id,int expected,Fields fields){var c=requireOwned(viewer,id);
        if(c.getBoolean("locked",false))throw new IllegalArgumentException("该版本已锁定，可制作新副本");
        if(expected!=c.getInt("revision",0))throw new IllegalArgumentException("记录已更新，请重新载入后编辑");
        c.put("fields",fields.nbt());c.putInt("revision",expected+1);markDirty();
    }
    public String duplicate(UUID viewer,String id){
        if(!recording(viewer))throw new IllegalArgumentException("当前未开放副本制作");
        var c=requireOwned(viewer,id);requireCapacity(viewer);
        String copyId=UUID.randomUUID().toString();var copy=c.copy();copy.putString("parent",id);copy.putBoolean("public",false);copy.putBoolean("locked",false);copy.putBoolean("in_bag",true);copy.putInt("revision",0);
        copy.remove("presenter");copy.remove("order");copy.remove("readers");copy.remove("wound_source");section("cards").put(copyId,copy);markDirty();return copyId;
    }
    public void setInBag(UUID viewer,String id,boolean bag){var c=requireOwned(viewer,id);if(c.getBoolean("in_bag",true)==bag)throw new IllegalArgumentException("卡片存放状态已变化");c.putBoolean("in_bag",bag);markDirty();}
    public boolean inBag(UUID viewer,String id){return requireOwned(viewer,id).getBoolean("in_bag",true);}
    public void publish(UUID viewer,String id){var c=card(id);
        if(!readable(viewer,id))throw new IllegalArgumentException("无权出示该记录");
        if(c.getBoolean("public",false))return;
        lock(viewer,id);c.putBoolean("public",true);markDirty();
    }
    public void lock(UUID viewer,String id){var c=card(id);if(!readable(viewer,id))throw new IllegalArgumentException("无权出示该记录");if(c.getBoolean("locked",false))return;
        requireOwned(viewer,id);c.putBoolean("locked",true);c.putString("presenter",run().getCompoundOrEmpty("players").getString(viewer.toString(),"玩家"));long order=run().getLong("order",0)+1;run().putLong("order",order);c.putLong("order",order);markDirty();
    }
    public void rememberShare(String shareId,UUID recipient,String cardId){var share=new NbtCompound();share.putString("card",cardId);share.putString("target",recipient.toString());share.putString("session",session());section("shares").put(shareId,share);markDirty();}
    public String openShare(UUID viewer,String shareId){var share=section("shares").getCompoundOrEmpty(shareId);if(!participant(viewer)||!session().equals(share.getString("session",""))||!viewer.toString().equals(share.getString("target","")))return "";
        String id=share.getString("card","");var c=card(id);if(!c.getBoolean("locked",false)||!session().equals(c.getString("session","")))return "";
        var readers=c.getCompoundOrEmpty("readers");readers.putBoolean(viewer.toString(),true);c.put("readers",readers);markDirty();return id;
    }
    public void compare(UUID viewer,String a,String b){
        if(a.equals(b)||!readable(viewer,a)||!readable(viewer,b))throw new IllegalArgumentException("请选择两张不同且有权阅读的卡片");
        publish(viewer,a);publish(viewer,b);var list=run().getListOrEmpty("comparisons");var entry=new NbtCompound();entry.putString("a",a);entry.putString("b",b);entry.putString("by",viewer.toString());list.add(entry);run().put("comparisons",list);markDirty();
    }
    public String[] latest(UUID viewer){if(!participant(viewer))return new String[]{"",""};var list=run().getListOrEmpty("comparisons");if(list.isEmpty())return new String[]{"",""};var e=list.getCompound(list.size()-1).orElseThrow();return new String[]{e.getString("a",""),e.getString("b","")};}
    public boolean collect(UUID viewer,String id){if(!readable(viewer,id))throw new IllegalArgumentException("无权读取该记录");if(owns(viewer,id))return false;
        var all=section("collected");var mine=all.getCompoundOrEmpty(viewer.toString());boolean fresh=!mine.getBoolean(id,false);mine.putBoolean(id,true);all.put(viewer.toString(),mine);markDirty();return fresh;
    }
    public List<Summary> summaries(UUID viewer){List<Summary> result=new ArrayList<>();
        for(String id:section("cards").getKeys())if(readable(viewer,id)){var c=card(id);result.add(new Summary(id,owns(viewer,c),c.getBoolean("locked",false),c.getBoolean("in_bag",true),c.getInt("revision",0),Fields.read(c.getCompoundOrEmpty("fields")),c.getString("presenter",""),section("collected").getCompoundOrEmpty(viewer.toString()).getBoolean(id,false),c.getString("group",""),c.getString("group_label","")));}
        result.sort(Comparator.comparingLong((Summary s)->card(s.id()).getLong("order",Long.MAX_VALUE)).thenComparing(Summary::id));return result;
    }
    public NbtCompound snapshot(UUID viewer,String id){if(!readable(viewer,id))throw new IllegalArgumentException("无权读取该记录");return section("snapshots").getCompoundOrEmpty(card(id).getString("snapshot","")).copy();}
}
