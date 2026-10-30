package li.cil.oc.integration.enderio;

import li.cil.oc.integration.Mods;
import scala.reflect.ScalaSignature;

@ScalaSignature(
   bytes = "\u0006\u0001Q:Q!\u0001\u0002\t\u00025\t!\"T8e\u000b:$WM]%P\u0015\t\u0019A!A\u0004f]\u0012,'/[8\u000b\u0005\u00151\u0011aC5oi\u0016<'/\u0019;j_:T!a\u0002\u0005\u0002\u0005=\u001c'BA\u0005\u000b\u0003\r\u0019\u0017\u000e\u001c\u0006\u0002\u0017\u0005\u0011A.[\u0002\u0001!\tqq\"D\u0001\u0003\r\u0015\u0001\"\u0001#\u0001\u0012\u0005)iu\u000eZ#oI\u0016\u0014\u0018jT\n\u0004\u001fIQ\u0002CA\n\u0019\u001b\u0005!\"BA\u000b\u0017\u0003\u0011a\u0017M\\4\u000b\u0003]\tAA[1wC&\u0011\u0011\u0004\u0006\u0002\u0007\u001f\nTWm\u0019;\u0011\u0005maR\"\u0001\u0003\n\u0005u!!\u0001C'pIB\u0013x\u000e_=\t\u000b}yA\u0011\u0001\u0011\u0002\rqJg.\u001b;?)\u0005i\u0001\"\u0002\u0012\u0010\t\u0003\u001a\u0013AB4fi6{G\rF\u0001%!\t)\u0003F\u0004\u0002\u001cM%\u0011q\u0005B\u0001\u0005\u001b>$7/\u0003\u0002*U\tI1+[7qY\u0016lu\u000e\u001a\u0006\u0003O\u0011AQ\u0001L\b\u0005B5\n!\"\u001b8ji&\fG.\u001b>f)\u0005q\u0003CA\u00183\u001b\u0005\u0001$\"A\u0019\u0002\u000bM\u001c\u0017\r\\1\n\u0005M\u0002$\u0001B+oSR\u0004"
)
public final class ModEnderIO {
   public static void initialize() {
      ModEnderIO$.MODULE$.initialize();
   }

   public static Mods.SimpleMod getMod() {
      return ModEnderIO$.MODULE$.getMod();
   }
}
