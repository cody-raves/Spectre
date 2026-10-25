package net.spectre.client.gui;

import java.util.*;
/** Tracks actual activation order, independent of registry order and menu selection. */
public final class MascotFocus {
    private final LinkedHashSet<String> active=new LinkedHashSet<>();
    private final Set<String> seen=new HashSet<>();
    private final LinkedHashSet<String> tasks=new LinkedHashSet<>();
    private boolean initialized;
    private String action;
    private long actionUntil;
    public void toggled(String id,boolean enabled,boolean startupDefault){
        active.remove(id);
        if(enabled&&(initialized||!startupDefault)){active.add(id);actionUntil=0;}
    }
    public void action(String id,long now){action=id;actionUntil=now+5000;}
    public String update(Collection<String> enabled,Collection<String> startupEligible,long now){
        return update(enabled,startupEligible,List.of(),now);
    }
    public String update(Collection<String> enabled,Collection<String> startupEligible,Collection<String> working,long now){
        for(String id:enabled)if(!seen.contains(id)&&(initialized||startupEligible.contains(id)))active.add(id);
        active.removeIf(id->!enabled.contains(id));seen.clear();seen.addAll(enabled);initialized=true;
        tasks.removeIf(id->!working.contains(id));tasks.addAll(working);
        if(!tasks.isEmpty()){String chosen="idle";for(String id:tasks)chosen=id;return chosen;}
        if(action!=null&&now<actionUntil)return action;
        String chosen="idle";for(String id:active)chosen=id;return chosen;
    }
}
