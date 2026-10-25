package net.spectre.client.account;

import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
record Credentials(String name, UUID uuid, String accessToken, String secret) {
}
