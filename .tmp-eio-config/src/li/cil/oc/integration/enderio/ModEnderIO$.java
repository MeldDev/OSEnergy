package li.cil.oc.integration.enderio;

import li.cil.oc.api.IMC;
import li.cil.oc.integration.ModProxy;
import li.cil.oc.integration.Mods;
import li.cil.oc.integration.Mods.;

public final class ModEnderIO$ implements ModProxy {
   public static final ModEnderIO$ MODULE$;

   static {
      new ModEnderIO$();
   }

   public Mods.SimpleMod getMod() {
      return .MODULE$.EnderIO();
   }

   public void initialize() {
      IMC.registerWrenchTool("li.cil.oc.integration.enderio.EventHandlerEnderIO.useWrench");
      IMC.registerWrenchToolCheck("li.cil.oc.integration.enderio.EventHandlerEnderIO.isWrench");
   }

   private ModEnderIO$() {
      MODULE$ = this;
   }
}
