package com.kuilunfuzhe.monvhua.features.forensics.viewport;

import com.kuilunfuzhe.monvhua.renderer.body.special.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import java.util.List;

/** Read-only scene: no world entities, no animation ticks and no automatic focus yet. */
public final class EvidenceScene {
    private final CombinedBodySpecialModelRenderer renderer;
    private final ItemStack appearance;
    private final NbtCompound visual;
    private final Matrix4f root;
    private final boolean fallback;
    private final EvidenceEnvironmentLayer environment;
    private String selectedWound="";
    public EvidenceScene(NbtCompound observation,NbtList wounds){this(observation,wounds,null);}
    public EvidenceScene(NbtCompound observation,NbtList wounds,EvidenceEnvironmentLayer environment){
        this.environment=environment;
        renderer=new CombinedBodySpecialModelRenderer(MinecraftClient.getInstance().getLoadedEntityModels());
        visual=observation.getCompoundOrEmpty("visual").copy();
        fallback=observation.getBoolean("missing",false)||observation.getBoolean("partial",false)||visual.getString("body_pose_mode","").equals("true_skeletal");
        if(fallback) for(String key:List.copyOf(visual.getKeys())) if(key.startsWith("pose_")||key.startsWith("bend_")||key.equals("body_pose_mode")) visual.remove(key);
        visual.put("corpse_wounds",wounds.copy());
        appearance=new ItemStack(Items.NETHERITE_SCRAP);appearance.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(visual));
        if(observation.contains("profile")) ProfileComponent.CODEC.parse(NbtOps.INSTANCE,observation.get("profile")).result().ifPresent(p->appearance.set(DataComponentTypes.PROFILE,p));
        var list=observation.getListOrEmpty("root");float[] m=new float[16];
        root=new Matrix4f().scale(1,-1,1);
        if(!fallback&&list.size()==16){for(int i=0;i<16;i++)m[i]=list.getFloat(i).orElse(0f);Matrix4f supplied=new Matrix4f().set(m);if(supplied.isFinite()&&Math.abs(supplied.determinant())>1e-10)root.set(supplied);}
    }
    public boolean fallback(){return fallback;}
    /** Future autofocus controller consumes this selection; currently selection never moves the camera. */
    public void requestFocus(String woundId){selectedWound=woundId;}
    public String selectedWound(){return selectedWound;}
    /** Reserved scene layer. A later snapshot adapter can supply blocks without changing camera coordinates. */
    public boolean hasEnvironment(){return environment!=null;}

    public Prepared prepare(){
        var data=renderer.getData(appearance);
        if(data==null){var texture=Identifier.ofVanilla("textures/entity/player/wide/steve.png");data=new BodyPartSpecialModelRenderer.Data(RenderLayer.getEntityCutoutNoCull(texture),texture,"default",visual.copy());}
        var surfaces=renderer.evidenceSurfaces(data,root);
        Box bounds=null;
        for(var surface:surfaces)for(int i=0;i<8;i++){
            Box b=surface.box();Vector3f p=surface.transform().transformPosition(new Vector3f((float)((i&1)==0?b.minX:b.maxX),(float)((i&2)==0?b.minY:b.maxY),(float)((i&4)==0?b.minZ:b.maxZ)));
            Box point=new Box(p.x,p.y,p.z,p.x,p.y,p.z);bounds=bounds==null?point:bounds.union(point);
        }
        if(environment!=null)bounds=bounds.union(environment.bounds());
        var captured=data;
        return new Prepared(bounds.expand(.04),surfaces,(matrices,consumers)->{
            if(environment!=null)environment.render(matrices,consumers);
            matrices.push();matrices.multiplyPositionMatrix(root);
            matrices.peek().getNormalMatrix().mul(new Matrix3f(root).invert().transpose());
            renderer.renderEvidence(matrices,consumers,captured);matrices.pop();
        });
    }
    public record Prepared(Box bounds,List<CombinedBodySpecialModelRenderer.EvidenceSurface> surfaces,EvidenceViewportRenderState.Scene render){
        public Anchor anchor(NbtCompound wound,EvidenceViewportFrame frame){
            String name=wound.getString("surface","");
            var surface=surfaces.stream().filter(s->s.name().equals(name)).findFirst().orElse(null);
            if(surface==null)return new Anchor(null,"伤口位置未记录");
            Direction face;try{face=Direction.valueOf(wound.getString("face",""));}catch(IllegalArgumentException e){return new Anchor(null,"伤口位置未记录");}
            double u=wound.getDouble("u",Double.NaN),v=wound.getDouble("v",Double.NaN);
            if(!Double.isFinite(u)||!Double.isFinite(v)||u<0||u>1||v<0||v>1)return new Anchor(null,"伤口位置未记录");
            Box b=surface.box();float x=(float)(b.minX+u*b.getLengthX()),y=(float)(b.minY+v*b.getLengthY()),z=(float)(b.minZ+u*b.getLengthZ());
            switch(face){case UP,DOWN->{y=(float)(face==Direction.UP?b.minY:b.maxY);z=(float)(b.minZ+v*b.getLengthZ());}case NORTH,SOUTH->z=(float)(face==Direction.NORTH?b.minZ:b.maxZ);case WEST,EAST->x=(float)(face==Direction.WEST?b.minX:b.maxX);}
            Vector3f normal=new Vector3f(face.getOffsetX(),face.getOffsetY(),face.getOffsetZ());
            if(face.getAxis()==Direction.Axis.Y)normal.y=-normal.y;
            Vector3f local=new Vector3f(x,y,z).fma(.035f,normal);
            Vector3f world=surface.transform().transformPosition(local);
            new Matrix3f(surface.transform()).invert().transpose().transform(normal).normalize();
            if(normal.dot(frame.eye().sub(world))<=0)return new Anchor(null,"伤口在背面，请用箭头转动视角");
            for(var other:surfaces){
                Matrix4f inverse=new Matrix4f(other.transform()).invert();
                Vector3f from=inverse.transformPosition(frame.eye()),to=inverse.transformPosition(new Vector3f(world));
                if(other.box().raycast(new Vec3d(from.x,from.y,from.z),new Vec3d(to.x,to.y,to.z)).isPresent())return new Anchor(null,"伤口被身体遮挡，请转动视角");
            }
            var point=frame.project(world);return new Anchor(point,point==null?"伤口位于视口外":"");
        }
    }
    public record Anchor(EvidenceViewportFrame.Point point,String message){}
}
