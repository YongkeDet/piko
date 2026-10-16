import app.crimera.bytecode.*;
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import java.io.*;
import java.util.*;

/** Frozen-APK diagnostic only: native IDs below are not production matching rules. */
public class GlassLabelRelocationRepro {
 public static void main(String[] args)throws Exception {
  var dex=DexFileFactory.loadDexContainer(new File(args[0]),Opcodes.getDefault());Method original=null;
  for(String e:dex.getDexEntryNames())for(ClassDef c:dex.getEntry(e).getDexFile().getClasses())if(c.getType().equals("Lcom/x/ui/common/e2;"))for(Method m:c.getMethods())if(m.getName().equals("invoke"))original=m;
  if(original==null)throw new AssertionError("No frozen bottom bar");
  int target=-1,index=0;for(Instruction i:original.getImplementation().getInstructions()){
   if(i instanceof ReferenceInstruction r&&r.getReference() instanceof MethodReference m&&m.getDefiningClass().equals("Landroidx/compose/ui/Modifier;")&&m.getReturnType().equals("Landroidx/compose/ui/Modifier;"))target=index;index++;
  }
  if(target<0)throw new AssertionError("No join");
  for(boolean relocate:List.of(true,false))try{
   HookKt.insertHook(new MutableMethod(original),target,List.of(),relocate,b->{b.invokeStatic(new ImmutableMethodReference("Lfixture/Hook;","noop",List.of(),"V"));return kotlin.Unit.INSTANCE;});
   System.out.println("relocate="+relocate+" PASS");if(relocate)throw new AssertionError("Expected isolated 0.1.3 failure");
  }catch(IllegalStateException e){if(!relocate||!e.getMessage().contains("hasn't been placed"))throw e;System.out.println("relocate=true reproduced");e.printStackTrace(System.out);}
 }
}
