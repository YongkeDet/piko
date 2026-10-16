import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import java.nio.file.*;
import java.util.*;

/** Final DEX: execute emitted model operands; prove actor preservation and native-only UI reachability. */
public class UnifiedBannerStaticReview extends FixedRefreshStaticReview {
 static final String N="Lapp/morphe/extension/newx/misc/NewXInAppNotification;", D="Lapp/morphe/extension/newx/misc/DownloadProgressDisplay;";
 static class Value {String type,text;Value(String t){type=t;}}
 static boolean zero(Object o){return o==null||o instanceof Number n&&n.longValue()==0;}
 static void sender(int kind,boolean detail,boolean facade) {
  Method m=method(N,"sendRich");var c=code(m);Object[] r=new Object[m.getImplementation().getRegisterCount()];int p=r.length-3;
  r[p]="title";r[p+1]=detail?"detail":null;r[p+2]=kind;
  boolean built=false,registered=false,sent=false;int steps=0;
  for(int i=0;i<c.size()&&steps++<200;){Instruction ins=c.get(i);Opcode op=ins.getOpcode();int next=i+1;
   if(op==Opcode.CONST_4||op==Opcode.CONST_16||op==Opcode.CONST)r[((OneRegisterInstruction)ins).getRegisterA()]=((NarrowLiteralInstruction)ins).getNarrowLiteral();
   else if(op==Opcode.SGET_OBJECT){var f=(FieldReference)((ReferenceInstruction)ins).getReference();r[((OneRegisterInstruction)ins).getRegisterA()]=f.getName().equals("facade")?(facade?"facade":null):f.getName();}
   else if(op==Opcode.NEW_INSTANCE)r[((OneRegisterInstruction)ins).getRegisterA()]=new Value(((TypeReference)((ReferenceInstruction)ins).getReference()).getType());
   else if(op==Opcode.MOVE||op==Opcode.MOVE_FROM16||op==Opcode.MOVE_OBJECT||op==Opcode.MOVE_OBJECT_FROM16||op==Opcode.MOVE_16||op==Opcode.MOVE_OBJECT_16){var v=(TwoRegisterInstruction)ins;r[v.getRegisterA()]=r[v.getRegisterB()];}
   else if(op==Opcode.IF_EQZ){if(zero(r[((OneRegisterInstruction)ins).getRegisterA()]))next=target(c,i);}
   else if(op==Opcode.IF_EQ){var v=(TwoRegisterInstruction)ins;if(Objects.equals(r[v.getRegisterA()],r[v.getRegisterB()]))next=target(c,i);}
   else if(op==Opcode.GOTO||op==Opcode.GOTO_16||op==Opcode.GOTO_32)next=target(c,i);
   else if(op.toString().startsWith("INVOKE_")){
    var ref=ref(ins);int[] a=regs(ins);
    if(ref.getName().equals("<init>")&&ref.getParameterTypes().equals(List.of("Ljava/lang/String;"))){((Value)r[a[0]]).text=(String)r[a[1]];}
    else if(ref.getName().equals("<init>")&&ref.getDefiningClass().startsWith("Lcom/x/inappnotification/api/")){
     check(((Value)r[a[1]]).text.equals("title"),"Native title kind="+kind+" detail="+detail);
     check(detail?((Value)r[a[5]]).text.equals("detail"):zero(r[a[5]]),"Native secondary detail kind="+kind+" detail="+detail);
     check(Objects.equals(r[a[7]],kind==1?"Success":kind==2?"Error":"Default"),"Native style kind="+kind+" detail="+detail);
     check(Objects.equals(r[a[8]],kind==3?"Low":kind==2?"High":"Normal"),"Native priority kind="+kind+" detail="+detail);
     int mask=((Number)r[a[a.length-1]]).intValue();
     check((mask&(16|128|256))==0&&(mask&8)!=0&&(mask&512)!=0&&(mask&1024)!=0,"Actual default mask supplies detail/style/priority and preserves other defaults kind="+kind+" detail="+detail);
     built=true;
    } else if(ref.getDefiningClass().equals(N)&&ref.getName().equals("registerNative")){check(built&&Objects.equals(r[a[1]],kind),"Ownership precedes sending kind="+kind+" detail="+detail);registered=true;}
    else if(ref.getDefiningClass().startsWith("Lcom/x/inappnotification/impl/")){check(built&&registered,"Facade receives registered native model kind="+kind+" detail="+detail);sent=true;}
    else throw new AssertionError("Unexpected rich sender call "+ref);
   } else if(op==Opcode.RETURN){check(Objects.equals(r[((OneRegisterInstruction)ins).getRegisterA()],facade?1:0),"Host availability result kind="+kind+" detail="+detail+" facade="+facade);check(sent==facade,"Absent host never receives a control notice kind="+kind+" detail="+detail);return;}
   else if(op!=Opcode.CHECK_CAST)throw new AssertionError("Unsupported rich sender opcode "+op);
   i=next;
  }
  throw new AssertionError("Rich sender did not terminate");
 }
 static Set<Integer> reachable(List<Instruction> c,int start){
  var seen=new HashSet<Integer>();var todo=new ArrayDeque<Integer>();todo.add(start);
  while(!todo.isEmpty()){int n=todo.remove();if(n<0||n>=c.size()||!seen.add(n))continue;var op=c.get(n).getOpcode();
   if(op.toString().startsWith("RETURN")||op==Opcode.THROW)continue;
   if(op.toString().startsWith("GOTO")){todo.add(target(c,n));continue;}
   if(op.toString().startsWith("IF_"))todo.add(target(c,n));
   if(op==Opcode.PACKED_SWITCH||op==Opcode.SPARSE_SWITCH)throw new AssertionError("Unexpected switch in progress tick");
   todo.add(n+1);
  }return seen;
 }
 static boolean asyncCall(Method m,String owner,String name,Set<String> visited){
  if(!visited.add(m.toString()))return false;
  if(call(m,owner,name))return true;
  for(MethodReference r:calls(m)){
   ClassDef cls=classes.get(r.getDefiningClass());
   if(cls==null||!cls.getType().startsWith("Lapp/morphe/extension/newx/"))continue;
   if(r.getName().equals("<init>")&&cls.getInterfaces().contains("Ljava/lang/Runnable;")){
    for(Method run:cls.getMethods())if(run.getName().equals("run")&&run.getParameterTypes().isEmpty()&&asyncCall(run,owner,name,visited))return true;
   }else for(Method target:cls.getMethods())if(target.toString().equals(r.toString())&&asyncCall(target,owner,name,visited))return true;
  }
  return false;
 }
 public static void main(String[] a)throws Exception{
  classes.putAll(load(a[0]));var before=load(a[1]);
  var enabled=code(method(N,"hasUnifiedHost"));check(enabled.get(0) instanceof NarrowLiteralInstruction l&&l.getNarrowLiteral()==1&&enabled.get(1).getOpcode()==Opcode.RETURN,"Native adapter enabled in final DEX");
  for(int kind=0;kind<5;kind++)for(boolean detail:List.of(false,true))sender(kind,detail,true);
  sender(3,true,false);
  var actors=new ArrayList<Method>();for(ClassDef cls:classes.values())for(Method m:cls.getMethods())if(call(m,N,"interceptNative"))actors.add(m);
  check(actors.size()==1,"Exactly one native actor intercepts Show events");var actor=actors.get(0);var ac=code(actor);int hook=index(ac,N,"interceptNative");
  check(ac.get(hook-1).getOpcode()==Opcode.MOVE_RESULT_OBJECT&&ac.get(hook+1).getOpcode()==Opcode.MOVE_RESULT&&ac.get(hook+2).getOpcode()==Opcode.IF_NEZ,"Decoded entry and hook invoke/result pairs remain valid");
  check(regs(ac.get(hook))[0]==actor.getImplementation().getRegisterCount()-2&&regs(ac.get(hook))[1]==((OneRegisterInstruction)ac.get(hook-1)).getRegisterA(),"Hook receives original manager and decoded entry registers");
  int continuation=target(ac,hook+2);check(ac.get(continuation).getOpcode().toString().startsWith("GOTO"),"Consumed events reach the original loop continuation");
  Method original=exact(before,actor);check(actor.getImplementation().getRegisterCount()==original.getImplementation().getRegisterCount(),"Actor register file unchanged");
  check(normalized(ac,Set.of(hook,hook+1,hook+2)).equals(normalized(code(original),Set.of())),"Every original actor instruction, register, reference, literal and branch preserved");
  Method intercept=method(N,"interceptNative");var ic=code(intercept);var nativeCalls=calls(intercept).stream().filter(r->r.getDefiningClass().startsWith("Lcom/x/inappnotification/impl/")).toList();
  var present=nativeCalls.stream().filter(r->r.getReturnType().equals("V")&&r.getParameterTypes().size()==1&&r.getParameterTypes().get(0).toString().startsWith("Lcom/x/inappnotification/impl/")).findFirst().orElseThrow();
  var dispose=nativeCalls.stream().filter(r->r.getReturnType().equals("V")&&r.getParameterTypes().size()==2).findFirst().orElseThrow();
  int show=index(ic,present.getDefiningClass(),present.getName());var disposalIndices=new ArrayList<Integer>();for(int n=0;n<ic.size();n++)if(ic.get(n) instanceof ReferenceInstruction rr&&rr.getReference().toString().equals(dispose.toString()))disposalIndices.add(n);
  check(disposalIndices.size()==2&&show<disposalIndices.get(0),"New entry is published before old entry disposal");
  check(regs(ic.get(disposalIndices.get(0)))[1]!=regs(ic.get(disposalIndices.get(1)))[1],"Replacement disposes old entry and rejected controls dispose incoming entry");
  for(MethodReference r:nativeCalls){Method m=resolve(r);check(normalized(code(m),Set.of()).equals(normalized(code(exact(before,m)),Set.of())),"Native presentation/disposal/accessor unchanged: "+r);}
  check(call(intercept,N,"decideNative"),"Actor uses ownership/severity/generation policy");
  check(AccessFlags.PUBLIC.isSet(intercept.getAccessFlags())&&AccessFlags.STATIC.isSet(intercept.getAccessFlags()),"Actor bridge is accessible public static");
  Method tick=method(D,"tick");var tc=code(tick);int gate=index(tc,N,"hasUnifiedHost");
  check(tc.get(gate+1).getOpcode()==Opcode.MOVE_RESULT,"Adapter gate boolean uses the direct invoke result");
  int gateReg=((OneRegisterInstruction)tc.get(gate+1)).getRegisterA();int branch=-1;
  for(int n=gate+2;n<tc.size();n++){var ins=tc.get(n);if((ins.getOpcode()==Opcode.IF_EQZ||ins.getOpcode()==Opcode.IF_NEZ)&&((OneRegisterInstruction)ins).getRegisterA()==gateReg){branch=n;break;}
   if(ins instanceof OneRegisterInstruction rr&&rr.getRegisterA()==gateReg&&!ins.getOpcode().toString().startsWith("INVOKE"))throw new AssertionError("Adapter gate overwritten before its branch: "+ins.getOpcode());
  }
  check(branch>=0,"Native UI branch consumes the adapter gate value");
  int start=tc.get(branch).getOpcode()==Opcode.IF_NEZ?target(tc,branch):branch+1;var nativePath=reachable(tc,start);
  check(nativePath.contains(index(tc,N,"progress")),"Enabled adapter reaches real native progress call");
  check(nativePath.stream().noneMatch(n->tc.get(n) instanceof ReferenceInstruction rr&&rr.getReference() instanceof MethodReference r&&(r.getName().equals("attach")||r.getName().equals("addView"))),"Enabled adapter cannot reach second-card creation");
  check(call(tick,"Landroid/app/Activity;","hasWindowFocus"),"Progress yields while activity has no focused window");
  check(call(tick,N,"finishProgress"),"Idle progress sends targeted clear");
  Method errorReport=null;for(Method m:classes.get("Lapp/morphe/extension/newx/misc/InlineDownloadButton;").getMethods())if(m.getName().equals("reportDownloadStatus")&&m.getParameterTypes().size()==1)errorReport=m;
  check(errorReport!=null&&call(errorReport,N,"showError"),"Download failures use unified error path");
  check(asyncCall(method("Lapp/morphe/extension/newx/misc/NativeDownloadRouter;","postStatus"),N,"tryShowError",new HashSet<>()),"Native download failures share the error severity");
  check(call(method("Lapp/morphe/extension/newx/misc/NativeDownloadRouter;","enqueue"),N,"hasUnifiedHost"),"Native queued announcement yields to real progress");
  for(String name:List.of("progress","finishProgress","tryShowSuccess","tryShowError"))check(calls(method(N,name)).stream().noneMatch(r->r.getDefiningClass().equals("Landroid/widget/Toast;")||r.getName().toLowerCase(Locale.ROOT).contains("toast")),"Top-only feedback has no bottom Toast: "+name);
  var report=new LinkedHashMap<String,Object>();report.put("apk",a[0]);report.put("actor",actor.toString());report.put("checks",checks);report.put("passed",true);Files.writeString(Path.of(a[2]),json(report)+"\n");System.out.println("Passed "+checks.size()+" native-banner checks");
 }
}
