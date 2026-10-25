package com.example.client.mixin;

import net.spectre.client.gui.VanillaUi;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;

/** Measuring and drawing use the same font, so centering, wrapping and selection stay aligned. */
@Mixin(Font.class)
abstract class MixinVanillaUiFont {
    @Unique private static final FontDescription spectre$uiFont=new FontDescription.Resource(Identifier.fromNamespaceAndPath("spectre","ui"));
    @ModifyVariable(method="getGlyphSource",at=@At("HEAD"),argsOnly=true)
    private FontDescription spectre$typeface(FontDescription requested){
        return FontDescription.DEFAULT.equals(requested)&&VanillaUi.enabled()?spectre$uiFont:requested;
    }
}
