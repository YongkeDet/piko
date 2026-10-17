import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import java.nio.file.*;
import java.util.*;

/** Final APK evidence for .11; exact native renderer preservation and graphical progress ABI. */
public class FullBannerStaticReview extends PinnedBannerStaticReview {
 static final String COMPOSER="Landroidx/compose/runtime/Composer;", FN="Lkotlin/jvm/functions/Function0;", PROVIDER=N.substring(0,N.length()-1)+"$ProgressProvider;";
 static List<String> types(MethodReference r){return r.getParameterTypes().stream().map(Object::toString).toList();}
 static int width(String t){return t.equals("J")||t.equals("D")?2:1;}
 static int slot(List<String> ts,int i){return ts.subList(0,i).stream().mapToInt(FullBannerStaticReview::width).sum();}
 static boolean named(Instruction in,String name){return in instanceof ReferenceInstruction rr&&rr.getReference() instanceof MethodReference r&&r.getDefiningClass().equals(N)&&r.getName().equals(name);}
 static boolean literal(Instruction in,int register,int value){return in instanceof NarrowLiteralInstruction l&&l.getNarrowLiteral()==value&&in instanceof OneRegisterInstruction r&&r.getRegisterA()==register;}
 static Map<Integer,Integer> origins(Method m,int end){
  Map<Integer,Integer> o=new HashMap<>();int p=m.getImplementation().getRegisterCount()-types(m).stream().mapToInt(FullBannerStaticReview::width).sum();
  for(int n=0;n<types(m).size();n++)for(int w=0;w<width(types(m).get(n));w++)o.put(p++,n);
  var c=code(m);for(int n=0;n<end;n++){Instruction in=c.get(n);Opcode op=in.getOpcode();
   if(op.toString().startsWith("MOVE")&&!op.toString().startsWith("MOVE_RESULT")&&in instanceof TwoRegisterInstruction r){Integer v=o.get(r.getRegisterB());o.remove(r.getRegisterA());if(v!=null)o.put(r.getRegisterA(),v);}
   else if(op!=Opcode.CHECK_CAST&&op.setsRegister()&&in instanceof OneRegisterInstruction r){o.remove(r.getRegisterA());if(op.setsWideRegister())o.remove(r.getRegisterA()+1);}
  }return o;
 }
 record Packed(Object source){}
 static void indicator(boolean unknown,Map<String,ClassDef> before){
  Method m=method(N,"drawNativeProgress");var c=code(m);Object[] r=new Object[m.getImplementation().getRegisterCount()];int p=r.length-3;
  r[p]="provider";r[p+1]="composer";r[p+2]=unknown?1:0;Object pending=null;boolean drawn=false;
  for(int n=0,steps=0;n<c.size()&&steps++<200;){Instruction in=c.get(n);Opcode op=in.getOpcode();int next=n+1;
   if(op==Opcode.CONST_4||op==Opcode.CONST_16||op==Opcode.CONST||op==Opcode.CONST_HIGH16)r[((OneRegisterInstruction)in).getRegisterA()]=((NarrowLiteralInstruction)in).getNarrowLiteral();
   else if(op==Opcode.SGET_OBJECT)r[((OneRegisterInstruction)in).getRegisterA()]=((ReferenceInstruction)in).getReference();
   else if(op.toString().startsWith("MOVE_RESULT")){int dst=((OneRegisterInstruction)in).getRegisterA();r[dst]=pending;if(op==Opcode.MOVE_RESULT_WIDE)r[dst+1]=pending;}
   else if(op.toString().startsWith("MOVE")){var x=(TwoRegisterInstruction)in;r[x.getRegisterA()]=r[x.getRegisterB()];}
   else if(op==Opcode.IF_NEZ){if(!zero(r[((OneRegisterInstruction)in).getRegisterA()]))next=target(c,n);}
   else if(op.toString().startsWith("INVOKE_")){
    MethodReference call=ref(in);int[] a=regs(in);var ts=types(call);
    if(call.getDefiningClass().equals(N)){check(a.length==0&&List.of("progressAccent","progressTrack").contains(call.getName()),"Progress colors come from theme: unknown="+unknown);pending=call.getName();}
    else if(ts.equals(List.of("I"))&&call.getReturnType().equals("J")){
     check(List.of("progressAccent","progressTrack").contains(r[a[0]]),"Native Color receives unmodified theme ARGB: unknown="+unknown);
     check(c.get(n+1).getOpcode()==Opcode.MOVE_RESULT_WIDE,"Packed color uses wide invoke result: unknown="+unknown);pending=new Packed(r[a[0]]);
    }else if(ts.equals(List.of(M,"F"))&&call.getReturnType().equals(M)){
     check(r[a[0]] instanceof FieldReference&&Objects.equals(r[a[1]],Float.floatToRawIntBits(1f)),"Native progress fills the column width: unknown="+unknown);
     FieldReference empty=(FieldReference)r[a[0]];check(classes.get(empty.getType()).getInterfaces().contains(M),"Empty Modifier has native interface: unknown="+unknown);pending="fullWidth";
    }else if(call.getDefiningClass().startsWith("Landroidx/compose/material3/")&&call.getReturnType().equals("V")){
     check(!drawn&&a.length==ts.stream().mapToInt(FullBannerStaticReview::width).sum(),"Exactly one native progress indicator with all argument words: unknown="+unknown);
     for(int i=0;i<a.length;i++)if(a[i]!=i)throw new AssertionError("Noncontiguous progress argument "+i);
     check(Objects.equals(r[a[slot(ts,ts.indexOf(COMPOSER))]],"composer")&&Objects.equals(r[a[slot(ts,ts.indexOf(M))]],"fullWidth"),"Native indicator receives original Composer and width: unknown="+unknown);
     var longs=new ArrayList<Integer>();for(int i=0;i<ts.size();i++)if(ts.get(i).equals("J"))longs.add(slot(ts,i));
     check(longs.size()==2&&Objects.equals(r[a[longs.get(0)]],new Packed("progressAccent"))&&Objects.equals(r[a[longs.get(1)]],new Packed("progressTrack")),"Explicit accent and track colors in order: unknown="+unknown);
     for(int j:longs)check(Objects.equals(r[a[j]],r[a[j+1]]),"Color register pair intact: unknown="+unknown+" slot="+j);
     if(unknown){
      check(!ts.contains(FN)&&Arrays.stream(a).filter(x->Objects.equals(r[x],"provider")).count()==0,"Unknown total uses native animated indicator");
      List<Object> ints=new ArrayList<>();for(int i=0;i<ts.size();i++)if(ts.get(i).equals("I"))ints.add(r[a[slot(ts,i)]]);
      check(ints.equals(List.of(1,0)),"Animated indicator has round cap and clean changed flags");
     }else{
      check(ts.contains(FN)&&Objects.equals(r[a[slot(ts,ts.indexOf(FN))]],"provider"),"Determinate indicator uses task Snapshot provider");
      check(Objects.equals(r[a[a.length-1]],112)&&Objects.equals(r[a[a.length-2]],0),"Native defaults only cap, gap and stop marker; changed flags remain zero");
     }
     Method nativeMethod=resolve(call);check(normalized(code(nativeMethod),Set.of()).equals(normalized(code(exact(before,nativeMethod)),Set.of())),"Shared native progress implementation byte-identical: unknown="+unknown);drawn=true;
    }else throw new AssertionError("Unexpected progress helper invoke "+call);
   }else if(op==Opcode.RETURN_VOID){check(drawn,"Progress bridge returns only after drawing: unknown="+unknown);return;}
   else if(op!=Opcode.CHECK_CAST)throw new AssertionError("Unexpected progress opcode "+op);
   n=next;
  }throw new AssertionError("Progress bridge did not return");
 }
 public static void main(String[] a)throws Exception{
  PinnedBannerStaticReview.main(a);var before=load(a[1]);int texts=0,bars=0,renderers=0;
  for(ClassDef cls:classes.values())for(Method m:cls.getMethods())if(!m.getDefiningClass().equals(N)&&call(m,N,"fullContent")){
   renderers++;var c=code(m);var skip=new HashSet<Integer>();int localTexts=0,localBars=0;
   for(int n=0;n<c.size();n++){
    if(named(c.get(n),"fullContent")){
     texts++;localTexts++;check(c.get(n+1).getOpcode()==Opcode.MOVE_RESULT&&c.get(n+2).getOpcode()==Opcode.IF_EQZ&&target(c,n+2)==n+6,"Foreign notice bypasses exactly three text overrides: "+m);
     var text=ref(c.get(n+6));var ts=types(text);int[] args=regs(c.get(n+6));int control=-1;
     for(int j=0;j+3<ts.size();j++)if(ts.subList(j,j+4).equals(List.of("I","Z","I","I"))){if(control!=-1)throw new AssertionError("Ambiguous text tuple");control=j;}
     check(control>=0&&text.getDefiningClass().startsWith("Landroidx/compose/material3/"),"Native text ABI resolved: "+text);
     check(literal(c.get(n+3),args[slot(ts,control)],1)&&literal(c.get(n+4),args[slot(ts,control+1)],1)&&literal(c.get(n+5),args[slot(ts,control+2)],Integer.MAX_VALUE),"Owned content clips, wraps and uses unlimited lines with wide offsets: "+text);
     check(origins(m,n).get(regs(c.get(n))[0])!=null&&types(m).get(origins(m,n).get(regs(c.get(n))[0])).startsWith("Lcom/x/inappnotification/api/"),"Ownership uses a live original model alias: "+text);
     for(int j=0;j<6;j++)skip.add(n+j);
    }else if(named(c.get(n),"drawProgress")){
     bars++;localBars++;check(c.get(n-1) instanceof TwoRegisterInstruction&&c.get(n-1).getOpcode().toString().startsWith("MOVE_OBJECT"),"Composer staged after native detail text");
     var move=(TwoRegisterInstruction)c.get(n-1);var text=ref(c.get(n-2));var ts=types(text);int[] args=regs(c.get(n-2));
     check(ts.get(0).equals("Ljava/lang/String;")&&text.getDefiningClass().startsWith("Landroidx/compose/material3/"),"Bar follows detail/combined body rather than headline");
     check(move.getRegisterB()==args[slot(ts,ts.indexOf(COMPOSER))]&&regs(c.get(n))[1]==move.getRegisterA()&&move.getRegisterA()<=15,"Bar receives native text Composer within bounded invoke registers");
     check(c.subList(n+1,c.size()).stream().anyMatch(i->i instanceof ReferenceInstruction rr&&rr.getReference() instanceof MethodReference r&&r.getDefiningClass().equals(COMPOSER)),"Progress stays inside existing native composition");
     skip.add(n-1);skip.add(n);
    }
   }
   check(localBars==1&&localTexts==2,"Each renderer draws one bar, with all text fields expanded");
   check(m.getImplementation().getRegisterCount()==exact(before,m).getImplementation().getRegisterCount(),"Renderer register frame unchanged: "+m);
   check(normalized(c,skip).equals(normalized(code(exact(before,m)),Set.of())),"All original renderer instructions, styles, defaults, minLines and branches preserved: "+m);
  }
  check(renderers==2&&texts==4&&bars==2,"Both native banner renderers adapted, with four text gates and two bars");
  indicator(false,before);indicator(true,before);
  check(classes.get(PROVIDER).getInterfaces().contains(FN),"Progress callback implements Kotlin Function0 in final DEX");
  Method invoke=method(PROVIDER,"invoke");check(AccessFlags.PUBLIC.isSet(invoke.getAccessFlags())&&invoke.getReturnType().equals("Ljava/lang/Object;")&&call(invoke,N,"readValue")&&call(invoke,"Ljava/lang/Number;","floatValue"),"Task callback reads its Float Snapshot without reflection");
  check(call(invoke,"Ljava/lang/Math;","min")&&call(invoke,"Ljava/lang/Math;","max"),"Native progress fraction bounded to zero through one");
  Method draw=method(C,"drawProgress");check(call(draw,P,"progressNotice")&&call(draw,N,"readValue")&&call(draw,N,"drawNativeProgress"),"Only an owned active progress notice draws, with a composition Snapshot dependency");
  check(call(method(C,"fullContent"),P,"fullContent"),"Complete text follows registered notice ownership");
  Method pump=method(C,"pump");check(call(pump,PROVIDER,"<init>")&&calls(pump).stream().filter(r->r.getDefiningClass().equals(N)&&r.getName().equals("makeState")).count()==3,"One provider and three states constructed with the retained progress model");
  var updaters=new ArrayList<Method>();for(Method m:classes.get(C).getMethods())if(call(m,N,"writeState"))updaters.add(m);
  check(updaters.size()==1&&calls(updaters.get(0)).stream().filter(r->r.getDefiningClass().equals(N)&&r.getName().equals("writeState")).count()==3,"Tick updates title, detail and fraction without rebuilding the notice");
  check(!call(method(D,"tick"),"Ljava/lang/String;","substring"),"Full progress filename has no substring truncation");
  check(call(method(D,"tick"),D,"amount")&&call(method(D,"amount"),"Landroid/text/format/Formatter;","formatShortFileSize"),"Real size and speed formatting remains reachable");
  Method bulk=method(S,"updateProgress");check(pathCalls(bulk,unifiedPath(bulk),D,"amount"),"Native bulk detail includes size and speed");
  for(Method m:List.of(method(D,"tick"),bulk))check(calls(m).stream().anyMatch(r->r.getDefiningClass().equals(N)&&r.getName().equals("progress")&&types(r).equals(List.of("J","Ljava/lang/String;","Ljava/lang/String;","I"))),"Percentage is forwarded to shared progress state: "+m);
  var report=new LinkedHashMap<String,Object>();report.put("apk",a[0]);report.put("checks",checks);report.put("passed",true);Files.writeString(Path.of(a[2]),json(report)+"\n");System.out.println("Passed "+checks.size()+" full-banner checks");
 }
}
