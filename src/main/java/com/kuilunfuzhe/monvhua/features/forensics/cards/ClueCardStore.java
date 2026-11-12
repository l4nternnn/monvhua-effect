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
    public record Summary(String id,boolean owned,boolean locked,boolean inBag,int revision,Fields fields,String presenter,boolean collected,String group,String groupLabel,String part,String kind){}
    public ClueCardStore(){this(new NbtCompound());}
    private ClueCardStore(NbtCompound n){data=n;int schema=n.getInt("schema",1);if(schema!=1)throw new IllegalStateException("Unknown clue card schema "+schema);data.putInt("schema",1);}
    public static ClueCardStore get(MinecraftServer server){return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);}
    private NbtCompound section(String key){if(!data.contains(key))data.put(key,new NbtCompound());return data.getCompoundOrEmpty(key);}
    private NbtCompound card(String id){return section("cards").getCompoundOrEmpty(id);}
    public String session(){return data.getString("current","");}
    /** Public synchronization and blank-card identity; legacy cases keep their original identifier. */
    public String accessToken(){return session().isEmpty()?"":run().getString("token",session());}
    private NbtCompound run(){return section("runs").getCompoundOrEmpty(session());}
    public boolean participant(UUID viewer){return !session().isEmpty()&&run().getCompoundOrEmpty("players").contains(viewer.toString());}
    public boolean recording(UUID viewer){return participant(viewer)&&run().getBoolean("recording",false);}
    public record CaseEntry(String id,boolean current,boolean open,List<String> players,int cards){}
    public List<CaseEntry> cases(){var out=new ArrayList<CaseEntry>();for(String id:section("runs").getKeys()){
        var r=section("runs").getCompoundOrEmpty(id);var members=r.getCompoundOrEmpty("players");var names=members.getKeys().stream().map(k->members.getString(k,"")).sorted().toList();
        int count=0;for(String card:section("cards").getKeys())if(id.equals(card(card).getString("session","")))count++;
        out.add(new CaseEntry(id,id.equals(session()),r.getBoolean("recording",false)&&id.equals(session()),names,count));
    }out.sort(Comparator.comparing(CaseEntry::id));return List.copyOf(out);}
    public int adminRevision(){return data.getInt("admin_revision",0);}
    private void adminChanged(){data.putInt("admin_revision",adminRevision()+1);markDirty();}
    public int defaultBags(){return data.getInt("default_bags",1);}
    public int defaultCards(){return data.getInt("default_cards",8);}
    public void defaults(int bags,int cards){if(bags<1||bags>64||cards<1||cards>64)throw new IllegalArgumentException("默认数量应为 1 至 64");data.putInt("default_bags",bags);data.putInt("default_cards",cards);adminChanged();}
    public boolean hasCase(String id){return section("runs").contains(id);}
    private NbtCompound requireCase(String id){if(!hasCase(id))throw new IllegalArgumentException("案件不存在");return section("runs").getCompoundOrEmpty(id);}
    private static void validatePlayers(Map<UUID,String> players){if(players.isEmpty()||players.size()>16)throw new IllegalArgumentException("请选择 1 至 16 名案件参与者");}
    public void addCase(String id,Map<UUID,String> players){
        if(id==null||!id.matches("[a-zA-Z0-9_-]{1,40}"))throw new IllegalArgumentException("案件编号须为 1 至 40 位字母、数字、下划线或短横线");
        validatePlayers(players);if(hasCase(id))throw new IllegalArgumentException("已存在同名案件");
        if(section("runs").getSize()>=128)throw new IllegalArgumentException("案件数量已达上限（128件）");
        var r=new NbtCompound();var members=new NbtCompound();players.forEach((uuid,name)->members.putString(uuid.toString(),name));
        r.put("players",members);r.putString("token",UUID.randomUUID().toString());r.putBoolean("scoped_wounds",true);r.putBoolean("recording",false);r.put("comparisons",new NbtList());section("runs").put(id,r);section("deleted_runs").remove(id);adminChanged();
    }
    /** Existing card APIs project one active case. Switching closes the previous recording stage. */
    public void openCase(String id){var next=requireCase(id);if(!session().isEmpty()&&hasCase(session()))run().putBoolean("recording",false);data.putString("current",id);next.putBoolean("recording",true);adminChanged();}
    public void closeCase(String id){requireCase(id).putBoolean("recording",false);adminChanged();}
    public void start(String id,Map<UUID,String> players){addCase(id,players);openCase(id);}
    public void closeRecording(){if(!session().isEmpty())closeCase(session());}
    public void requireDistribution(Collection<UUID> targets){
        if(session().isEmpty()||!run().getBoolean("recording",false))throw new IllegalArgumentException("请先开启一个案件");
        for(UUID target:targets)if(!participant(target))throw new IllegalArgumentException("发放对象必须属于当前开启的案件");
    }
    public void deleteCase(String id){
        requireCase(id);var removed=new HashSet<String>();var snapshots=new HashSet<String>();
        for(String card:section("cards").getKeys())if(id.equals(card(card).getString("session",""))){removed.add(card);snapshots.add(card(card).getString("snapshot",""));}
        for(String card:removed)section("cards").remove(card);for(String snapshot:snapshots)section("snapshots").remove(snapshot);
        for(String share:new HashSet<>(section("shares").getKeys()))if(id.equals(section("shares").getCompoundOrEmpty(share).getString("session","")))section("shares").remove(share);
        for(String viewer:section("collected").getKeys()){var mine=section("collected").getCompoundOrEmpty(viewer);for(String card:removed)mine.remove(card);}
        section("runs").remove(id);if(id.equals(session()))data.putString("current","");adminChanged();
    }
    public void addMembers(String id,Map<UUID,String> members){
        var players=requireCase(id).getCompoundOrEmpty("players");var ids=new HashSet<>(players.getKeys());members.keySet().forEach(uuid->ids.add(uuid.toString()));
        if(members.isEmpty()||ids.size()>16)throw new IllegalArgumentException("案件参与者不得超过16人");
        members.forEach((uuid,name)->players.putString(uuid.toString(),name));adminChanged();
    }
    /** Explicit admin action, never infer ownership from a changed display name or login. */
    public void rebindMember(String id,String previous,UUID target,String name){
        var r=requireCase(id);var players=r.getCompoundOrEmpty("players");
        var matches=players.getKeys().stream().filter(key->key.equals(previous)||players.getString(key,"").equals(previous)).toList();
        if(matches.size()!=1)throw new IllegalArgumentException("请选择唯一的原参与者名称或UUID");
        String from=matches.getFirst(),to=target.toString();if(!from.equals(to)&&players.contains(to))throw new IllegalArgumentException("目标已经是案件参与者，不能合并身份");
        String oldName=players.getString(from,"");players.remove(from);players.putString(to,name);
        if(!from.equals(to)){
            var oldCollected=section("collected").getCompoundOrEmpty(from);var newCollected=section("collected").getCompoundOrEmpty(to);
            for(String cardId:section("cards").getKeys()){
                var c=card(cardId);if(!id.equals(c.getString("session","")))continue;
                if(from.equals(c.getString("owner","")))c.putString("owner",to);
                if(oldName.equals(c.getString("presenter","")))c.putString("presenter",name);
                var readers=c.getCompoundOrEmpty("readers");if(readers.contains(from)){readers.putBoolean(to,readers.getBoolean(from,false));readers.remove(from);}
                if(oldCollected.contains(cardId)){newCollected.putBoolean(cardId,oldCollected.getBoolean(cardId,false));oldCollected.remove(cardId);}
            }
            section("collected").put(to,newCollected);
            var observed=r.getCompoundOrEmpty("observed_corpses");if(observed.contains(from)){observed.put(to,observed.getCompoundOrEmpty(from).copy());observed.remove(from);}
            var corpseRecords=r.getCompoundOrEmpty("corpse_records");if(corpseRecords.contains(from)){corpseRecords.put(to,corpseRecords.getCompoundOrEmpty(from).copy());corpseRecords.remove(from);}
            for(var element:r.getListOrEmpty("comparisons"))if(element instanceof NbtCompound c&&from.equals(c.getString("by","")))c.putString("by",to);
            for(String key:section("shares").getKeys()){var share=section("shares").getCompoundOrEmpty(key);if(id.equals(share.getString("session",""))&&from.equals(share.getString("target","")))share.putString("target",to);}
        }
        adminChanged();
    }
    public int ownedCount(UUID viewer){int count=0;for(String id:section("cards").getKeys())if(owns(viewer,card(id)))count++;return count;}
    private int occupiedSlots(UUID viewer){var slots=new HashSet<String>();for(String id:section("cards").getKeys())if(owns(viewer,card(id))){String group=card(id).getString("group","");slots.add(group.isEmpty()?"card/"+id:"corpse/"+group);}return slots.size();}
    private boolean ownsCorpse(UUID viewer,String corpse){for(String id:section("cards").getKeys())if(owns(viewer,card(id))&&corpse.equals(card(id).getString("group","")))return true;return false;}
    private void requireCapacity(UUID viewer){requireCapacity(viewer,"");}
    private void requireCapacity(UUID viewer,String group){
        if((group.isEmpty()||!ownsCorpse(viewer,group))&&occupiedSlots(viewer)>=64)throw new IllegalArgumentException("卡袋已满");
        int total=0;for(String id:section("cards").getKeys())if(session().equals(card(id).getString("session","")))total++;if(total>=256)throw new IllegalArgumentException("本案件线索明细已达上限（256条）");
    }
    private boolean owns(UUID viewer,NbtCompound c){return participant(viewer)&&session().equals(c.getString("session",""))&&viewer.toString().equals(c.getString("owner",""));}
    public boolean owns(UUID viewer,String id){return owns(viewer,card(id));}
    private NbtCompound requireOwned(UUID viewer,String id){var c=card(id);if(!owns(viewer,c))throw new IllegalArgumentException("无权操作该卡片");return c;}
    public boolean readable(UUID viewer,String id){var c=card(id);return participant(viewer)&&session().equals(c.getString("session",""))&&(owns(viewer,c)||c.getBoolean("public",false)||c.getCompoundOrEmpty("readers").getBoolean(viewer.toString(),false));}
    public String create(UUID owner,Fields fields,NbtCompound snapshot,NbtCompound audit){return create(owner,fields,snapshot,audit,"","");}
    private String create(UUID owner,Fields fields,NbtCompound snapshot,NbtCompound audit,String group,String label){
        if(!recording(owner))throw new IllegalArgumentException("当前未开放记录");
        requireCapacity(owner,group);
        String id=UUID.randomUUID().toString();var c=new NbtCompound();c.putString("owner",owner.toString());c.putString("session",session());
        c.putString("root",id);c.putString("snapshot",id);c.put("fields",fields.nbt());c.put("audit",audit.copy());c.putInt("revision",0);c.putBoolean("in_bag",true);if(!group.isEmpty()){c.putString("group",group);c.putString("group_label",label);}
        section("snapshots").put(id,snapshot.copy());section("cards").put(id,c);markDirty();return id;
    }
    public void observeCorpse(UUID viewer,String corpse){if(!recording(viewer)||corpse.isEmpty())return;var observed=run().getCompoundOrEmpty("observed_corpses");var mine=observed.getCompoundOrEmpty(viewer.toString());mine.putBoolean(corpse,true);observed.put(viewer.toString(),mine);run().put("observed_corpses",observed);markDirty();}
    public boolean canImportCorpse(UUID viewer,String corpse){return participant(viewer)&&(!run().getBoolean("scoped_wounds",false)||corpseRecorded(viewer,corpse)||run().getCompoundOrEmpty("observed_corpses").getCompoundOrEmpty(viewer.toString()).getBoolean(corpse,false));}
    public record WoundImport(String id,boolean created,int blankCost){}
    public boolean corpseRecorded(UUID viewer,String corpse){return participant(viewer)&&(run().getCompoundOrEmpty("corpse_records").getCompoundOrEmpty(viewer.toString()).getBoolean(corpse,false)||ownsCorpse(viewer,corpse));}
    public String importWound(UUID viewer,String source,String group,String label,Fields fields,NbtCompound snapshot){return importWound(viewer,source,group,label,fields,snapshot,1).id();}
    public WoundImport importWound(UUID viewer,String source,String group,String label,Fields fields,NbtCompound snapshot,int availableBlanks){
        if(group.isEmpty()||source.isEmpty())throw new IllegalArgumentException("伤情记录缺少遗体或伤口编号");
        for(String id:section("cards").getKeys())if(owns(viewer,card(id))&&source.equals(card(id).getString("wound_source","")))return new WoundImport(id,false,0);
        int cost=corpseRecorded(viewer,group)?0:1;if(cost>availableBlanks)throw new IllegalArgumentException("首次收录该遗体的伤情需要一张空白卡");
        var audit=new NbtCompound();audit.putString("wound_source",source);String id=create(viewer,fields,snapshot,audit,group,label);card(id).putString("wound_source",source);
        var records=run().getCompoundOrEmpty("corpse_records");var mine=records.getCompoundOrEmpty(viewer.toString());mine.putBoolean(group,true);records.put(viewer.toString(),mine);run().put("corpse_records",records);markDirty();return new WoundImport(id,true,cost);
    }
    public int copyCost(UUID viewer,String id){return requireOwned(viewer,id).getString("group","").isEmpty()?1:0;}
    public void edit(UUID viewer,String id,int expected,Fields fields){var c=requireOwned(viewer,id);
        if(c.getBoolean("locked",false))throw new IllegalArgumentException("该记录已定稿，可另行制作副本");
        if(expected!=c.getInt("revision",0))throw new IllegalArgumentException("记录内容已更新，请重新打开后编辑");
        c.put("fields",fields.nbt());c.putInt("revision",expected+1);markDirty();
    }
    public String duplicate(UUID viewer,String id){
        if(!recording(viewer))throw new IllegalArgumentException("当前案件未开放副本制作");
        var c=requireOwned(viewer,id);requireCapacity(viewer,c.getString("group",""));
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
    public record Comparison(int number,String a,String b,String presenter){}
    public List<Comparison> history(UUID viewer){
        if(!participant(viewer))return List.of();var result=new ArrayList<Comparison>();var list=run().getListOrEmpty("comparisons");
        for(int i=Math.max(0,list.size()-64);i<list.size();i++){var e=list.getCompound(i).orElseThrow();String a=e.getString("a",""),b=e.getString("b","");
            if(readable(viewer,a)&&readable(viewer,b))result.add(new Comparison(i+1,a,b,run().getCompoundOrEmpty("players").getString(e.getString("by",""),"玩家")));}
        return List.copyOf(result);
    }
    public boolean collect(UUID viewer,String id){if(!readable(viewer,id))throw new IllegalArgumentException("无权读取该记录");if(owns(viewer,id))return false;
        var all=section("collected");var mine=all.getCompoundOrEmpty(viewer.toString());boolean fresh=!mine.getBoolean(id,false);mine.putBoolean(id,true);all.put(viewer.toString(),mine);markDirty();return fresh;
    }
    public List<Summary> summaries(UUID viewer){List<Summary> result=new ArrayList<>();
        for(String id:section("cards").getKeys())if(readable(viewer,id)){var c=card(id);result.add(new Summary(id,owns(viewer,c),c.getBoolean("locked",false),c.getBoolean("in_bag",true),c.getInt("revision",0),Fields.read(c.getCompoundOrEmpty("fields")),c.getString("presenter",""),section("collected").getCompoundOrEmpty(viewer.toString()).getBoolean(id,false),c.getString("group",""),c.getString("group_label",""),snapshotPart(c),snapshotKind(c)));}
        result.sort(Comparator.comparingLong((Summary s)->card(s.id()).getLong("order",Long.MAX_VALUE)).thenComparing(Summary::id));return result;
    }
    private String snapshotKind(NbtCompound c){var n=section("snapshots").getCompoundOrEmpty(c.getString("snapshot",""));return n.contains("item")?"item":n.contains("body")?"body":"scene";}
    private String snapshotPart(NbtCompound c){
        var snapshot=section("snapshots").getCompoundOrEmpty(c.getString("snapshot",""));
        var wounds=snapshot.getCompoundOrEmpty("body").getListOrEmpty("wounds");
        return wounds.isEmpty()?"":wounds.getCompound(0).orElseThrow().getString("part","");
    }
    public NbtCompound snapshot(UUID viewer,String id){if(!readable(viewer,id))throw new IllegalArgumentException("无权读取该记录");return section("snapshots").getCompoundOrEmpty(card(id).getString("snapshot","")).copy();}
}
