package net.spectre.client.util.playerbypass;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum LightSectionStatus {
   NORMAL,
   EMPTY_MASK,
   ZEROED_PRESENT,
   UNIFORM_MAX,
   UNKNOWN;
}
