package net.spectre.client.builder;

import java.util.*;

/** Concrete slot allocations for empty shulkers; boxes themselves travel separately. */
public record SupplyDepotPlan(List<Slot> slots,List<MaterialPlan.Row> carrySeparately,int boxes) {
    public record Slot(int box,int slot,String item,int count) { }
    public static SupplyDepotPlan pack(MaterialPlan materials) {
        List<Slot> slots=new ArrayList<>();List<MaterialPlan.Row> loose=new ArrayList<>();
        for(var row:materials.rows()) {
            if(row.required()<0)throw new IllegalArgumentException("Negative material count");
            if(!row.packable()){loose.add(row);continue;}
            long remaining=row.required();while(remaining>0){int count=(int)Math.min(row.maxStack(),remaining),index=slots.size();slots.add(new Slot(index/27,index%27,row.item(),count));remaining-=count;}
        }
        return new SupplyDepotPlan(List.copyOf(slots),List.copyOf(loose),(slots.size()+26)/27);
    }
}
