package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.google.gson.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Read-only schema 1 boundary. No Bullet, NBT, or world references. */
public record PhysicsPoseDocument(String outcome, int step, Map<String,Pose> poses, float tilt, String reason, String definition, float scale) {
    public PhysicsPoseDocument(String outcome,int step,Map<String,Pose> poses,float tilt,String reason,String definition){this(outcome,step,poses,tilt,reason,definition,1);}
    public static Set<String> validateParts(Set<String> ids) {
        if(ids.isEmpty()||!new HashSet<>(IDS).containsAll(ids)||ids.size()>1&&!ids.contains("torso"))throw new IllegalArgumentException("Invalid connected body subset");
        return Set.copyOf(ids);
    }
    public String rootPart(){return poses.containsKey("torso")?"torso":poses.keySet().iterator().next();}
    public static final List<String> IDS=List.of("head","torso","left_arm","right_arm","left_leg","right_leg");
    public record Pose(float x,float y,float z,float qx,float qy,float qz,float qw) {
        public Quaternionf rotation(){return new Quaternionf(qx,qy,qz,qw);}
    }
    public PhysicsPoseDocument(String outcome,int step,Map<String,Pose> poses,float tilt,String reason) {this(outcome,step,poses,tilt,reason,"standard_six_v1");}
    public PhysicsPoseDocument { CorpseScale.require(scale);validateParts(poses.keySet());poses=Map.copyOf(poses); }
    public static PhysicsPoseDocument parse(String text) {
        try {
            if(text.length()>256*1024)throw new IllegalArgumentException("Pose document too large");
            int depth=0;boolean quoted=false,escaped=false;
            for(int i=0;i<text.length();i++) {
                char c=text.charAt(i);
                if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;}
                else if(c=='"')quoted=true;
                else if(c=='{'||c=='['){if(++depth>32)throw new IllegalArgumentException("Pose nesting too deep");}
                else if(c=='}'||c==']')depth--;
            }
            JsonObject root=JsonParser.parseString(text).getAsJsonObject();
            finite(root);
            if(integer(root,"schema_version")!=1)throw new IllegalArgumentException("Unsupported schema_version");
            var def=root.getAsJsonObject("definition");
            String definition=def.get("id").getAsString();
            if(!Set.of("standard_six","slim_six").contains(definition))throw new IllegalArgumentException("Unknown skeleton");
            float scale=Float.NaN;var definitionParts=new HashSet<String>();
            for(var element:def.getAsJsonArray("parts")){var part=element.getAsJsonObject();var id=part.get("id").getAsString();if(!definitionParts.add(id))throw new IllegalArgumentException("Duplicate definition part");if(Float.isNaN(scale)||id.equals("torso"))scale=number(part.getAsJsonObject("size"),"y")/(id.equals("head")?.5f:.75f);}
            validateParts(definitionParts);
            CorpseScale.require(scale);
            try(var stream=PhysicsPoseDocument.class.getResourceAsStream("/assets/monvhua/physics_preview/"+(definition.equals("slim_six")?"slim":"standard")+"-six-definition.json")) {
                if(stream==null)throw new IllegalStateException("Missing standard skeleton resource");
                var expected=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                var expectedParts=new JsonArray();for(var e:expected.getAsJsonArray("parts"))if(definitionParts.contains(e.getAsJsonObject().get("id").getAsString()))expectedParts.add(e);
                var expectedJoints=new JsonArray();for(var e:expected.getAsJsonArray("joints")){var j=e.getAsJsonObject();if(definitionParts.contains(j.get("parent").getAsString())&&definitionParts.contains(j.get("child").getAsString()))expectedJoints.add(e);}
                expected.add("parts",expectedParts);expected.add("joints",expectedJoints);scaleDefinition(expected,scale);
                if(!sameGeometry(canonical(def),canonical(expected)))throw new IllegalArgumentException("Skeleton geometry does not match the pinned v1 definition");
            }
            var input=root.getAsJsonObject("input");
            var anchor=input.getAsJsonObject("sceneAnchor");
            for(String key:List.of("x","y","z"))if(Math.abs(number(anchor,key))>8)throw new IllegalArgumentException("Initial anchor outside region");
            if(Math.abs(number(input,"yawDegrees"))>360 || Math.abs(number(input,"leanDegrees"))>30 || Math.abs(number(input,"forwardSpeed"))>2)
                throw new IllegalArgumentException("Unsupported initial motion");
            String outcome=root.get("outcome").getAsString();
            if(!Set.of("SETTLED","TIMEOUT","LEFT_REGION","INVALID").contains(outcome))throw new IllegalArgumentException("Invalid outcome");
            var frame=root.getAsJsonObject("finalPose"); int step=integer(frame,"step");
            if(step<0||step>300)throw new IllegalArgumentException("Invalid frame step");
            Map<String,Pose> poses=new LinkedHashMap<>();
            for(var entry:frame.getAsJsonArray("parts")) {
                var part=entry.getAsJsonObject();String id=part.get("id").getAsString();
                if(!IDS.contains(id)||poses.containsKey(id))throw new IllegalArgumentException("Unknown/duplicate part: "+id);
                var t=part.getAsJsonObject("transform");
                float x=number(t,"x"),y=number(t,"y"),z=number(t,"z");
                if(Math.abs(x)>8||y< -8||y>8||Math.abs(z)>8)throw new IllegalArgumentException("Pose outside supported local region");
                var q=new Quaternionf(number(t,"qx"),number(t,"qy"),number(t,"qz"),number(t,"qw"));
                if(Math.abs(q.lengthSquared()-1)>1e-3f)throw new IllegalArgumentException("Invalid quaternion: "+id);
                q.normalize();poses.put(id,new Pose(x,y,z,q.x,q.y,q.z,q.w));
            }
            if(!poses.keySet().equals(definitionParts))throw new IllegalArgumentException("Pose topology does not match definition");
            float tilt=number(root,"torsoTiltDegrees");
            if(tilt<0||tilt>180)throw new IllegalArgumentException("Invalid torso tilt");
            for(String key:List.of("elapsedNanos","maxAnchorError","maxPenetration","maxLimitError"))
                if(number(root,key)<0)throw new IllegalArgumentException("Negative diagnostic: "+key);
            return new PhysicsPoseDocument(outcome,step,poses,tilt,root.get("reason").getAsString(),definition+"_v1",scale);
        } catch(IOException|IllegalStateException|NullPointerException|ClassCastException e){throw new IllegalArgumentException("Malformed pose document: "+e.getMessage(),e);}
    }
    private static void scaleDefinition(JsonObject definition,float scale) {
        for(var e:definition.getAsJsonArray("parts")) {
            var p=e.getAsJsonObject(); scaleVector(p.getAsJsonObject("size"),scale);scaleVector(p.getAsJsonObject("center"),scale);
            p.addProperty("mass",number(p,"mass")*scale*scale*scale);p.addProperty("inset",number(p,"inset")*scale);
        }
        for(var e:definition.getAsJsonArray("joints")) {
            var j=e.getAsJsonObject();scaleVector(j.getAsJsonObject("pivotA"),scale);scaleVector(j.getAsJsonObject("pivotB"),scale);
        }
    }
    private static void scaleVector(JsonObject v,float scale){for(String k:List.of("x","y","z"))v.addProperty(k,number(v,k)*scale);}
    private static boolean sameGeometry(JsonElement a,JsonElement b) {
        if(a.isJsonObject()&&b.isJsonObject()) {
            var x=a.getAsJsonObject();var y=b.getAsJsonObject();
            return x.keySet().equals(y.keySet())&&x.keySet().stream().allMatch(k->sameGeometry(x.get(k),y.get(k)));
        }
        if(a.isJsonArray()&&b.isJsonArray()) {
            var x=a.getAsJsonArray();var y=b.getAsJsonArray();if(x.size()!=y.size())return false;
            for(int i=0;i<x.size();i++)if(!sameGeometry(x.get(i),y.get(i)))return false;return true;
        }
        if(a.isJsonPrimitive()&&b.isJsonPrimitive()&&a.getAsJsonPrimitive().isNumber()&&b.getAsJsonPrimitive().isNumber()) {
            float x=a.getAsFloat(),y=b.getAsFloat();return Math.abs(x-y)<=4*Math.max(Math.ulp(x),Math.ulp(y));
        }
        return a.equals(b);
    }
    private static JsonObject canonical(JsonObject object) {
        var copy=object.deepCopy();
        for(String key:List.of("parts","joints")) {
            var elements=new ArrayList<JsonElement>();copy.getAsJsonArray(key).forEach(elements::add);
            elements.sort(Comparator.comparing(e->e.getAsJsonObject().get("id").getAsString()));
            var array=new JsonArray();elements.forEach(array::add);copy.add(key,array);
        }
        return copy;
    }
    private static int integer(JsonObject o,String k){if(!o.get(k).isJsonPrimitive()||!o.get(k).getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected number: "+k);double n=o.get(k).getAsDouble();if(n!=Math.rint(n)||n<Integer.MIN_VALUE||n>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid integer: "+k);return (int)n;}
    private static float number(JsonObject o,String k){if(!o.get(k).isJsonPrimitive()||!o.get(k).getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected number: "+k);float n=o.get(k).getAsFloat();if(!Float.isFinite(n))throw new IllegalArgumentException("Invalid number: "+k);return n;}
    private static void finite(JsonElement e) {
        if(e.isJsonObject())e.getAsJsonObject().entrySet().forEach(v->finite(v.getValue()));
        else if(e.isJsonArray())e.getAsJsonArray().forEach(PhysicsPoseDocument::finite);
        else if(e.isJsonPrimitive()&&e.getAsJsonPrimitive().isNumber()&&!Double.isFinite(e.getAsDouble()))throw new IllegalArgumentException("Non-finite input");
    }
}
