package crazypants.enderio.machine;

import crazypants.enderio.EnderIO;
import crazypants.enderio.conduit.ConnectionMode;
import crazypants.enderio.machine.IoMode.1;
import net.minecraft.util.EnumChatFormatting;

public enum IoMode {
   NONE("gui.machine.ioMode.none"),
   PULL("gui.machine.ioMode.pull"),
   PUSH("gui.machine.ioMode.push"),
   PUSH_PULL("gui.machine.ioMode.pullPush"),
   DISABLED("gui.machine.ioMode.disabled");

   private final String unlocalisedName;

   private IoMode(String unlocalisedName) {
      this.unlocalisedName = unlocalisedName;
   }

   public String getUnlocalisedName() {
      return this.unlocalisedName;
   }

   public static ConnectionMode getNext(ConnectionMode mode) {
      int ord = mode.ordinal() + 1;
      if (ord >= ConnectionMode.VALUES.length) {
         ord = 0;
      }

      return ConnectionMode.VALUES[ord];
   }

   public static ConnectionMode getPrevious(ConnectionMode mode) {
      int ord = mode.ordinal() - 1;
      if (ord < 0) {
         ord = ConnectionMode.VALUES.length - 1;
      }

      return ConnectionMode.VALUES[ord];
   }

   public boolean pulls() {
      return this == PULL || this == PUSH_PULL;
   }

   public boolean pushes() {
      return this == PUSH || this == PUSH_PULL;
   }

   public boolean canOutput() {
      return this.pushes() || this == NONE;
   }

   public boolean canRecieveInput() {
      return this.pulls() || this == NONE;
   }

   public String getLocalisedName() {
      return EnderIO.lang.localize(this.unlocalisedName);
   }

   public String colorLocalisedName() {
      String loc = this.getLocalisedName();
      switch (1.$SwitchMap$crazypants$enderio$machine$IoMode[this.ordinal()]) {
         case 1:
            return EnumChatFormatting.RED + loc;
         case 2:
            return EnumChatFormatting.GRAY + loc;
         case 3:
            return EnumChatFormatting.AQUA + loc;
         case 4:
            return EnumChatFormatting.GOLD + loc;
         case 5:
            return String.format(EnderIO.lang.localize(this.getUnlocalisedName() + ".colored"), EnumChatFormatting.GOLD, EnumChatFormatting.WHITE, EnumChatFormatting.AQUA);
         default:
            return loc;
      }
   }

   public IoMode next() {
      int index = this.ordinal() + 1;
      if (index >= values().length) {
         index = 0;
      }

      return values()[index];
   }

   // $FF: synthetic method
   private static IoMode[] $values() {
      return new IoMode[]{NONE, PULL, PUSH, PUSH_PULL, DISABLED};
   }
}
