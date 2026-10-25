package net.spectre.client.module.impl.movement.elytrafly;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum ElytraFlightModes {
   Vanilla,
   Packet,
   Pitch40,
   Bounce;
}
