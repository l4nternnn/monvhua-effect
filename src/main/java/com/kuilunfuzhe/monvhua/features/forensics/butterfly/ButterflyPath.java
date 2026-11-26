package com.kuilunfuzhe.monvhua.features.forensics.butterfly;

import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Resumable 26-neighbour A*. Every edge, including diagonal and goal connections, is swept. */
public final class ButterflyPath {
    @FunctionalInterface public interface Clearance { boolean clear(Vec3d from, Vec3d to); }
    public static final class Yield extends RuntimeException {
        public Yield(){super(null,null,false,false);}
    }
    private record Cell(int x,int y,int z) {}
    private record Node(Cell cell,double g,double f) {}
    private final Vec3d start,goal;
    private final PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble(Node::f));
    private final Map<Cell,Double> distance=new HashMap<>();
    private final Map<Cell,Cell> parent=new HashMap<>();
    private final Set<Cell> closed=new HashSet<>();
    private List<Vec3d> result=List.of();
    private boolean done;
    public ButterflyPath(Vec3d start,Vec3d goal){
        this.start=start;this.goal=goal;
        Cell origin=new Cell(0,0,0);distance.put(origin,0.0);open.add(new Node(origin,0,start.distanceTo(goal)));
    }
    private Vec3d point(Cell c){return start.add(c.x*.5,c.y*.5,c.z*.5);}
    public boolean done(){return done;}
    public List<Vec3d> result(){return result;}
    public int nodes(){return distance.size();}
    public void step(Clearance space,int expansions){
        while(!done&&expansions-->0){
            Node node=open.poll();
            if(node==null){done=true;return;}
            if(closed.contains(node.cell)||node.g>distance.getOrDefault(node.cell,Double.POSITIVE_INFINITY))continue;
            Vec3d from=point(node.cell);
            try {
                if(from.squaredDistanceTo(goal)<=1&&space.clear(from,goal)){
                    var path=new ArrayList<Vec3d>();path.add(goal);
                    for(Cell c=node.cell;c!=null;c=parent.get(c))path.add(point(c));
                    Collections.reverse(path);result=List.copyOf(path);done=true;return;
                }
                for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
                    if(x==0&&y==0&&z==0)continue;
                    Cell next=new Cell(node.cell.x+x,node.cell.y+y,node.cell.z+z);
                    if(Math.abs(next.x)>48||Math.abs(next.y)>24||Math.abs(next.z)>48||closed.contains(next))continue;
                    Vec3d to=point(next);double cost=node.g+from.distanceTo(to);
                    if(cost>=distance.getOrDefault(next,Double.POSITIVE_INFINITY)||!space.clear(from,to))continue;
                    if((distance.size()>=4096&&!distance.containsKey(next))||open.size()>=16384){done=true;return;}
                    distance.put(next,cost);parent.put(next,node.cell);open.add(new Node(next,cost,cost+to.distanceTo(goal)));
                }
                closed.add(node.cell);
            }catch(Yield pause){open.add(node);return;}
        }
    }
    /** Non-accumulating visual drift; callers still sweep the actual segment. */
    public static Vec3d drift(Vec3d previous,Vec3d nativeMotion,double radius){
        Vec3d next=previous.multiply(.88).add(nativeMotion.multiply(.35));
        return next.lengthSquared()>radius*radius?next.normalize().multiply(radius):next;
    }
}
