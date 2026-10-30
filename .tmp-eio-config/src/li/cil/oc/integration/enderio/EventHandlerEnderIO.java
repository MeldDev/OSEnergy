package li.cil.oc.integration.enderio;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import scala.reflect.ScalaSignature;

@ScalaSignature(
   bytes = "\u0006\u0001\u0011;Q!\u0001\u0002\t\u00025\t1#\u0012<f]RD\u0015M\u001c3mKJ,e\u000eZ3s\u0013>S!a\u0001\u0003\u0002\u000f\u0015tG-\u001a:j_*\u0011QAB\u0001\fS:$Xm\u001a:bi&|gN\u0003\u0002\b\u0011\u0005\u0011qn\u0019\u0006\u0003\u0013)\t1aY5m\u0015\u0005Y\u0011A\u00017j\u0007\u0001\u0001\"AD\b\u000e\u0003\t1Q\u0001\u0005\u0002\t\u0002E\u00111#\u0012<f]RD\u0015M\u001c3mKJ,e\u000eZ3s\u0013>\u001b\"a\u0004\n\u0011\u0005M1R\"\u0001\u000b\u000b\u0003U\tQa]2bY\u0006L!a\u0006\u000b\u0003\r\u0005s\u0017PU3g\u0011\u0015Ir\u0002\"\u0001\u001b\u0003\u0019a\u0014N\\5u}Q\tQ\u0002C\u0003\u001d\u001f\u0011\u0005Q$A\u0005vg\u0016<&/\u001a8dQR1a$\t\u00184k]\u0002\"aE\u0010\n\u0005\u0001\"\"a\u0002\"p_2,\u0017M\u001c\u0005\u0006Em\u0001\raI\u0001\u0007a2\f\u00170\u001a:\u0011\u0005\u0011bS\"A\u0013\u000b\u0005\t2#BA\u0014)\u0003\u0019)g\u000e^5us*\u0011\u0011FK\u0001\n[&tWm\u0019:bMRT\u0011aK\u0001\u0004]\u0016$\u0018BA\u0017&\u00051)e\u000e^5usBc\u0017-_3s\u0011\u0015y3\u00041\u00011\u0003\u0005A\bCA\n2\u0013\t\u0011DCA\u0002J]RDQ\u0001N\u000eA\u0002A\n\u0011!\u001f\u0005\u0006mm\u0001\r\u0001M\u0001\u0002u\")\u0001h\u0007a\u0001=\u0005\u00012\r[1oO\u0016$UO]1cS2LG/\u001f\u0005\u0006u=!\taO\u0001\tSN<&/\u001a8dQR\u0011a\u0004\u0010\u0005\u0006{e\u0002\rAP\u0001\u0006gR\f7m\u001b\t\u0003\u007f\tk\u0011\u0001\u0011\u0006\u0003\u0003\"\nA!\u001b;f[&\u00111\t\u0011\u0002\n\u0013R,Wn\u0015;bG.\u0004"
)
public final class EventHandlerEnderIO {
   public static boolean isWrench(ItemStack var0) {
      return EventHandlerEnderIO$.MODULE$.isWrench(var0);
   }

   public static boolean useWrench(EntityPlayer var0, int var1, int var2, int var3, boolean var4) {
      return EventHandlerEnderIO$.MODULE$.useWrench(var0, var1, var2, var3, var4);
   }
}
