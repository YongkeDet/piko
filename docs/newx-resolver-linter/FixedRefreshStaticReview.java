import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.reandroid.apk.ApkModule;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Offline verification only: compare every original bottom-host instruction and branch. */
public class FixedRefreshStaticReview extends GlassNavStaticReview {
 static final String V="Lapp/morphe/extension/newx/misc/BottomBarVisibility;", T="Lapp/morphe/extension/newx/timeline/TimelineRefreshGate;";
 static Map<String,ClassDef> load(String file)throws Exception {
  var result=new HashMap<String,ClassDef>();var d=DexFileFactory.loadDexContainer(new File(file),Opcodes.getDefault());
  for(String e:d.getDexEntryNames())for(ClassDef c:d.getEntry(e).getDexFile().getClasses())result.put(c.getType(),c);return result;
 }
 static int target(List<Instruction> c,int index){int[] pc=new int[c.size()+1];for(int n=0;n<c.size();n++)pc[n+1]=pc[n]+c.get(n).getCodeUnits();return Arrays.binarySearch(pc,pc[index]+((OffsetInstruction)c.get(index)).getCodeOffset());}
 static List<String> normalized(List<Instruction> c,Set<Integer> skip){var out=new ArrayList<String>();for(int n=0;n<c.size();n++){if(skip.contains(n))continue;var i=c.get(n);String s=i.getOpcode().toString();if(i instanceof FiveRegisterInstruction||i instanceof RegisterRangeInstruction)s+=Arrays.toString(regs(i));else{if(i instanceof OneRegisterInstruction r)s+=" a"+r.getRegisterA();if(i instanceof TwoRegisterInstruction r)s+=" b"+r.getRegisterB();if(i instanceof ThreeRegisterInstruction r)s+=" c"+r.getRegisterC();}if(i instanceof ReferenceInstruction r)s+=" "+r.getReference();if(i instanceof WideLiteralInstruction l)s+=" #"+l.getWideLiteral();if(i instanceof OffsetInstruction){int t=target(c,n);if(t<0||skip.contains(t))throw new AssertionError("Invalid native target "+t);int shift=0;for(int k:skip)if(k<t)shift++;s+=" ->"+(t-shift);}out.add(s);}return out;}
 static Method exact(Map<String,ClassDef> map,Method ref){for(Method m:map.get(ref.getDefiningClass()).getMethods())if(m.toString().equals(ref.toString()))return m;throw new AssertionError(ref);}
 public static void main(String[] a)throws Exception{
  classes.putAll(load(a[0]));var before=load(a[1]);
  var hosts=new ArrayList<Method>();for(ClassDef c:classes.values())for(Method m:c.getMethods())if(call(m,V,"keepVisible"))hosts.add(m);
  check(hosts.size()==1,"Fixed bar affects exactly one main host");Method host=hosts.get(0);var hc=code(host);int hook=index(hc,V,"keepVisible");
  check(hc.get(hook-2).getOpcode()==Opcode.INVOKE_VIRTUAL&&ref(hc.get(hook-2)).toString().equals("Ljava/lang/Boolean;->booleanValue()Z")&&hc.get(hook-1).getOpcode()==Opcode.MOVE_RESULT,"Scroll visibility unboxed immediately before hook");
  int vr=((OneRegisterInstruction)hc.get(hook-1)).getRegisterA();
  check(regs(hc.get(hook))[0]==vr&&hc.get(hook+1).getOpcode()==Opcode.MOVE_RESULT&&((OneRegisterInstruction)hc.get(hook+1)).getRegisterA()==vr,"Hook updates only the native scroll-visible register");
  check(hc.get(hook+2).getOpcode()==Opcode.IF_NEZ&&((OneRegisterInstruction)hc.get(hook+2)).getRegisterA()==vr,"Native visibility branch consumes the result");
  Method original=exact(before,host);
  check(host.getImplementation().getRegisterCount()==original.getImplementation().getRegisterCount(),"Bottom host register file unchanged");
  check(normalized(hc,Set.of(hook,hook+1)).equals(normalized(code(original),Set.of())),"Every original bottom-host opcode, register, reference, literal and branch target preserved");
  check(hc.subList(0,hook).stream().anyMatch(i->i instanceof OffsetInstruction o&&target(hc,hc.indexOf(i))>hook+1),"Outer hide paths can bypass scroll override");
  check(call(method(V,"keepVisible"),"Lapp/morphe/extension/crimera/settings/SettingsRegistry;","getBooleanOrDefault"),"Fixed bar reads independent runtime setting");
  var handlers=new ArrayList<Method>();for(ClassDef c:classes.values())for(Method m:c.getMethods())if(call(m,T,"isAutomaticRefresh")&&call(m,T,"shouldBlockAutomaticRefresh"))handlers.add(m);
  check(handlers.size()==1,"Background gate affects exactly one native repository request handler");Method repo=handlers.get(0);var rc=code(repo);int auto=index(rc,T,"isAutomaticRefresh"), block=index(rc,T,"shouldBlockAutomaticRefresh");
  check(auto==0,"Automatic request classifier runs at repository entry");
  check(rc.get(auto+1).getOpcode()==Opcode.MOVE_RESULT&&rc.get(auto+2).getOpcode()==Opcode.IF_EQZ,"Manual and paging requests bypass the entire added gate");
  int nativeEntry=target(rc,auto+2);check(nativeEntry>block&&rc.get(block+3).getOpcode()==Opcode.RETURN_VOID,"Only blocked automatic updates return before the existing handler");
  check(rc.get(block+1).getOpcode()==Opcode.MOVE_RESULT&&rc.get(block+2).getOpcode()==Opcode.IF_EQZ&&target(rc,block+2)==nativeEntry,"Allowed automatic loads reach the same original path");
  check(rc.subList(0,nativeEntry).stream().noneMatch(i->i instanceof OffsetInstruction&&i instanceof TwoRegisterInstruction),"Cursor presence is not used to bypass automatic-update policy");
  check(regs(rc.get(auto))[0]==repo.getImplementation().getRegisterCount()-repo.getParameterTypes().size(),"Request classifier receives the request enum parameter");
  check(regs(rc.get(block))[0]==regs(rc.get(auto))[0],"Policy receives the same original request enum");
  var requestOwner=classes.get(repo.getParameterTypes().get(0).toString());var names=new HashSet<String>();requestOwner.getFields().forEach(f->names.add(f.getName()));
  check(names.containsAll(List.of("PULL_TO_REFRESH","AUTO_REFRESH","VIEWPORT_AWARE_AUTO_REFRESH","OLDER_THAN","NEWER_THAN","GAP")),"Target request contract distinguishes automatic, manual and paging operations");
  String display="Lapp/morphe/extension/newx/misc/DownloadProgressDisplay;", model="Lapp/morphe/extension/newx/misc/DownloadProgressState;", dl="Lapp/morphe/extension/newx/misc/NewXDownloader;";
  check(call(method(dl,"install"),display,"<init>"),"Progress display registered with the real download engine");
  check(call(method(display,"onProgress"),"Lapp/morphe/extension/crimera/downloader/events/DownloadEvent$Progress;","bytesDone")&&call(method(display,"onProgress"),"Lapp/morphe/extension/crimera/downloader/events/DownloadEvent$Progress;","totalBytes")&&call(method(display,"onProgress"),model,"progress"),"Progress consumes real done/total byte events");
  for(String name:List.of("onCompleted","onFailed","onCancelled"))check(call(method(display,name),display,"end"),"Terminal callback removes progress: "+name);
  check(call(method(display,"end"),model,"finished"),"Terminal cleanup deletes the active transfer");
  check(call(method(display,"tick"),"Landroid/widget/ProgressBar;","setIndeterminate")&&call(method(display,"tick"),"Landroid/widget/ProgressBar;","setProgress"),"Foreground card supports known and unknown lengths");
  Method bp=method("Lapp/morphe/extension/newx/misc/ProfileBulkDownload$Session;","updateProgress");
  check(call(bp,display,"batchSnapshot")&&call(bp,display,"amount")&&call(bp,"Landroid/widget/ProgressBar;","setProgress"),"Profile batch dialog consumes current transfer byte progress");
  check(!call(method(display,"tick"),"Landroid/widget/Toast;","makeText"),"Progress UI does not introduce a bottom toast");
  String merge="Lapp/morphe/extension/newx/misc/MediaMerger;";
  check(call(method(merge,"performMerge"),display,"beginLocal")&&call(method(merge,"performMerge"),display,"localStage")&&call(method(merge,"performMerge"),display,"endLocal"),"Image merge tracks slices and clears progress in its exit paths");
  check(call(method(merge,"downloadToFile"),display,"localProgress")&&calls(method(merge,"downloadToFile")).stream().anyMatch(r->r.getDefiningClass().startsWith("Ljava/net/")&&r.getName().equals("getContentLengthLong")&&r.getReturnType().equals("J")),"Merge download progress uses actual byte count and HTTP length");
  var expected=strings(Path.of(a[3]).resolve("patches/src/main/resources/addresources/values-zh-rCN/newx/strings.xml"));
  try(ApkModule apk=ApkModule.loadApkFile(new File(a[0]))){apk.setLoadDefaultFramework(false);for(String key:List.of("piko_newx_fixed_bottom_bar_title","piko_newx_fixed_bottom_bar_summary","piko_newx_disable_timeline_refresh_title","piko_newx_disable_timeline_refresh_summary","piko_newx_download_progress_active","piko_newx_download_progress_amount","piko_newx_download_progress_unknown_amount","piko_newx_download_progress_waiting","piko_newx_download_progress_merge_part","piko_newx_download_progress_merging")){boolean found=false;var entries=apk.getTableBlock().getEntries(null,"string",key);while(entries.hasNext()){var e=entries.next();if("zh".equals(e.getResConfig().getLanguage())&&"CN".equals(e.getResConfig().getRegion())&&expected.get(key).equals(e.getValueAsString()))found=true;}check(found,"Compiled Chinese setting matches source: "+key);}}
  var report=new LinkedHashMap<String,Object>();report.put("apk",a[0]);report.put("host",host.toString());report.put("repository",repo.toString());report.put("checks",checks);report.put("passed",true);Files.writeString(Path.of(a[2]),json(report)+"\n");System.out.println("Passed "+checks.size()+" fixed-bar/automatic-refresh checks");
 }
}
