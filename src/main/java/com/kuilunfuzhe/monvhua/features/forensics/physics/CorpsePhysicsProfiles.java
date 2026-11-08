package com.kuilunfuzhe.monvhua.features.forensics.physics;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Pure configuration contract; safe to load even when the optional native core is absent. */
public final class CorpsePhysicsProfiles {
    public record Solver(int iterations,float linearDamping,float angularDamping,float friction,float rollingFriction,float spinningFriction){
        public Solver{
            if(iterations<30||iterations>80)throw new IllegalArgumentException("iterations must be 30..80");
            range(linearDamping,.15f,.5f);range(angularDamping,.35f,.75f);range(friction,.4f,1);
            range(rollingFriction,0,.06f);range(spinningFriction,0,.06f);
        }
    }
    public record Motion(float impactSpeed,float unknownSpeed,float maxBaseline,float maxSpeed,float tilt,float angularSpeed){
        public Motion{
            range(impactSpeed,.2f,.6f);range(unknownSpeed,.2f,.6f);range(maxBaseline,0,.4f);range(maxSpeed,.4f,.8f);
            range(tilt,0,1);range(angularSpeed,.4f,.7f);
            if(maxSpeed<Math.max(impactSpeed,unknownSpeed))throw new IllegalArgumentException("maxSpeed below impact speed");
        }
    }
    public record Profile(String id,Solver solver,Motion motion){
        public Profile{if(id==null||!id.matches("[a-z][a-z0-9_]{0,47}"))throw new IllegalArgumentException("Invalid profile ID");Objects.requireNonNull(solver);Objects.requireNonNull(motion);}
        public String hash(){
            // Record order and Float.toString are fixed; input whitespace/key order are irrelevant.
            try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("schema1;algorithm2;"+id+";"+solver+";"+motion).getBytes(StandardCharsets.UTF_8)));}
            catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
        }
    }
    public record Registry(String defaultId,Map<String,Profile> profiles){
        public Registry{profiles=Map.copyOf(profiles);if(!profiles.containsKey(defaultId))throw new IllegalArgumentException("Missing default profile");}
    }
    public static final Profile F5=new Profile("corpse_f5_v1",new Solver(60,.3f,.6f,.8f,.05f,.05f),new Motion(.4f,.4f,.4f,.8f,.5f,.6f));
    public static Registry baseline(){return new Registry(F5.id(),Map.of(F5.id(),F5));}
    private static void range(float v,float a,float b){if(!Float.isFinite(v)||v<a||v>b)throw new IllegalArgumentException("Value outside "+a+".."+b);}
    public static Registry parse(String text){
        if(text.getBytes(StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("Profile file exceeds 64 KiB");
        try(var reader=new JsonReader(new StringReader(text))){
            reader.setStrictness(Strictness.STRICT);
            var root=read(reader,0).getAsJsonObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Trailing JSON");
            keys(root,"schema","default","profiles");if(integer(root,"schema")!=1)throw new IllegalArgumentException("Unsupported profile schema");
            var array=root.get("profiles").getAsJsonArray();if(array.size()>8)throw new IllegalArgumentException("At most eight user profiles");
            var profiles=new LinkedHashMap<String,Profile>();profiles.put(F5.id(),F5);
            for(var element:array){
                var o=element.getAsJsonObject();keys(o,"id","solver","motion");String id=string(o,"id");
                var s=o.getAsJsonObject("solver");keys(s,"iterations","linearDamping","angularDamping","friction","rollingFriction","spinningFriction");
                var m=o.getAsJsonObject("motion");keys(m,"impactSpeed","unknownSpeed","maxBaseline","maxSpeed","tilt","angularSpeed");
                var p=new Profile(id,new Solver(integer(s,"iterations"),number(s,"linearDamping"),number(s,"angularDamping"),number(s,"friction"),number(s,"rollingFriction"),number(s,"spinningFriction")),
                        new Motion(number(m,"impactSpeed"),number(m,"unknownSpeed"),number(m,"maxBaseline"),number(m,"maxSpeed"),number(m,"tilt"),number(m,"angularSpeed")));
                if(profiles.putIfAbsent(id,p)!=null)throw new IllegalArgumentException("Duplicate/reserved profile ID: "+id);
            }
            return new Registry(string(root,"default"),profiles);
        }catch(IOException|RuntimeException e){throw new IllegalArgumentException("Invalid profile JSON: "+e.getMessage(),e);}
    }
    private static JsonElement read(JsonReader r,int depth)throws IOException{
        if(depth>8)throw new IllegalArgumentException("JSON nesting exceeds eight levels");
        return switch(r.peek()){
            case BEGIN_OBJECT->{var o=new JsonObject();r.beginObject();while(r.hasNext()){String key=r.nextName();if(o.has(key))throw new IllegalArgumentException("Duplicate field: "+key);o.add(key,read(r,depth+1));}r.endObject();yield o;}
            case BEGIN_ARRAY->{var a=new JsonArray();r.beginArray();while(r.hasNext())a.add(read(r,depth+1));r.endArray();yield a;}
            case STRING->new JsonPrimitive(r.nextString());
            case NUMBER->new JsonPrimitive(new java.math.BigDecimal(r.nextString()));
            case BOOLEAN->new JsonPrimitive(r.nextBoolean());
            case NULL->{r.nextNull();yield JsonNull.INSTANCE;}
            default->throw new IllegalArgumentException("Unexpected JSON token");
        };
    }
    private static void keys(JsonObject o,String... keys){if(!o.keySet().equals(Set.of(keys)))throw new IllegalArgumentException("Expected fields "+Arrays.toString(keys)+", got "+o.keySet());}
    private static String string(JsonObject o,String k){var p=o.getAsJsonPrimitive(k);if(!p.isString())throw new IllegalArgumentException("Expected string: "+k);return p.getAsString();}
    private static float number(JsonObject o,String k){var p=o.getAsJsonPrimitive(k);if(!p.isNumber())throw new IllegalArgumentException("Expected number: "+k);float f=p.getAsFloat();if(!Float.isFinite(f))throw new IllegalArgumentException("Non-finite number: "+k);return f;}
    private static int integer(JsonObject o,String k){var p=o.getAsJsonPrimitive(k);if(!p.isNumber())throw new IllegalArgumentException("Expected integer: "+k);try{return p.getAsBigDecimal().intValueExact();}catch(ArithmeticException e){throw new IllegalArgumentException("Expected integer: "+k,e);}}
    public static String example(){
        var root=new JsonObject();root.addProperty("schema",1);root.addProperty("default",F5.id());var entries=new JsonArray();
        entries.add(new Gson().toJsonTree(new Profile("corpse_custom_v1",F5.solver(),F5.motion())));root.add("profiles",entries);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root)+"\n";
    }
}
