package net.spectre.client.builder;

import java.util.*;

public record MaterialPlan(List<Row> rows, List<String> unresolved, long storageSlots, long shulkers) {
    public record Supply(String item,int maxStack,int units,boolean packable) {
        public Supply { if(maxStack<1||units<0)throw new IllegalArgumentException("Invalid supply"); }
    }
    public record Row(String item,long required,int maxStack,boolean packable) { }
    public interface Resolver { Supply resolve(String state) throws Exception; }
    public static MaterialPlan calculate(Map<String,Long> stateCounts,Resolver resolver) {
        Map<String,Row> totals=new TreeMap<>();List<String> unresolved=new ArrayList<>();
        for(var e:stateCounts.entrySet()) try {
            Supply s=resolver.resolve(e.getKey());if(s.units==0)continue;
            long count=Math.multiplyExact(e.getValue(),s.units);Row old=totals.get(s.item);
            if(old!=null&&(old.maxStack!=s.maxStack||old.packable!=s.packable))throw new IllegalArgumentException("Inconsistent item properties");
            totals.put(s.item,new Row(s.item,Math.addExact(old==null?0:old.required,count),s.maxStack,s.packable));
        } catch(Exception ex) {unresolved.add(e.getValue()+" x "+e.getKey()+": "+ex.getMessage());}
        long slots=totals.values().stream().filter(Row::packable).mapToLong(r->(r.required+r.maxStack-1)/r.maxStack).sum();
        return new MaterialPlan(List.copyOf(totals.values()),List.copyOf(unresolved),slots,(slots+26)/27);
    }
    public static Map<String,Long> countStates(List<Schematic.Block> blocks) {
        Map<String,Long> counts=new TreeMap<>();for(var b:blocks)counts.merge(b.state(),1L,Long::sum);return counts;
    }
}
