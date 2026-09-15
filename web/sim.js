export const clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
export const lerp=(a,b,t)=>a+(b-a)*t;
export const wrap=a=>Math.atan2(Math.sin(a),Math.cos(a));
export const length=v=>Math.hypot(v.x,v.y,v.z);
export const distance=(a,b)=>Math.hypot(a.x-b.x,a.y-b.y,a.z-b.z);
export const forward=(yaw,pitch=0)=>({x:Math.sin(yaw)*Math.cos(pitch),y:Math.sin(pitch),z:Math.cos(yaw)*Math.cos(pitch)});
const smooth=t=>t*t*(3-2*t);
function hash(x,z,seed){let n=Math.imul(x,374761393)+Math.imul(z,668265263)+Math.imul(seed,1274126177);n=Math.imul(n^(n>>>13),1274126177);return ((n^(n>>>16))>>>0)/4294967295;}
export function noise(x,z,seed=0){const a=Math.floor(x),b=Math.floor(z),u=smooth(x-a),v=smooth(z-b);return lerp(lerp(hash(a,b,seed),hash(a+1,b,seed),u),lerp(hash(a,b+1,seed),hash(a+1,b+1,seed),u),v);}
function fbm(x,z,s){return noise(x,z,s)*.57+noise(x*2.1,z*2.1,s+7)*.27+noise(x*4.4,z*4.4,s+19)*.11+noise(x*9,z*9,s+41)*.05;}
export const MAPS=[
 {id:0,name:'СЕВЕРНЫЙ РУБЕЖ',area:'ГОРНАЯ ДОЛИНА',brief:'Три командных пункта в горной долине. Рельеф закрывает цели от радара. Уничтожьте пункты управления и вернитесь к маяку снабжения.',seed:21,sky:0x8fb5cc,fog:0xa5bbca,water:0x376a80,sun:0xffedd6,bases:[{x:-2500,z:-2800},{x:5300,z:2800},{x:-6200,z:7900}],start:{x:-4000,z:-8500}},
 {id:1,name:'ТИХИЙ АРХИПЕЛАГ',area:'ОСТРОВНОЕ ПОБЕРЕЖЬЕ',brief:'Вражеские базы на трёх островах. Над открытой водой вас легко обнаружить. Следите за воздушными целями и запасом ракет.',seed:64,sky:0x729ab8,fog:0x8eafc3,water:0x1b6374,sun:0xffedce,bases:[{x:-2800,z:-2200},{x:5400,z:1900},{x:-4800,z:8500}],start:{x:-4500,z:-8600}},
 {id:2,name:'КРАСНЫЙ КАНЬОН',area:'ПУСТЫННОЕ ПЛАТО',brief:'Сеть укреплений среди каньонов. Зенитные комплексы прикрывают командные пункты. Не теряйте высоту на подходе к скалам.',seed:113,sky:0xbca490,fog:0xc6b29c,water:0x766653,sun:0xffd6a0,bases:[{x:-2500,z:-2500},{x:5800,z:1500},{x:-6500,z:7800}],start:{x:-4000,z:-8500}}
];
export const AIRCRAFT=[{id:'su57',name:'Су-57',health:100,missiles:12,gun:900,flares:24,cruise:285,maxSpeed:455}];
function rawHeight(m,x,z){
 const n=fbm(x/3100,z/3100,m.seed),r=1-Math.abs(noise(x/2200,z/2200,m.seed+43)*2-1);
 if(m.id===0)return 85+n*n*1250+r*r*r*660+noise(x/420,z/420,m.seed)*75;
 if(m.id===1){let island=-170;for(const b of [...m.bases,m.start,{x:9500,z:9000},{x:-10000,z:3000},{x:4500,z:-9500}])island=Math.max(island,660-Math.hypot(x-b.x,z-b.z)*.2);return island+(n-.45)*540;}
 const valley=Math.abs(Math.sin(x/1800+Math.sin(z/3100)*1.4));
 return 110+n*250+smooth(clamp((valley-.24)*2.2,0,1))*760+r*130;
}
export function terrainHeight(m,x,z){let h=rawHeight(m,x,z);for(const b of [...m.bases,m.start]){const r=Math.hypot(x-b.x,z-b.z),w=1-smooth(clamp((r-390)/440,0,1));if(w>0)h=lerp(h,Math.max(75,rawHeight(m,b.x,b.z)),w);}return h;}
export function lineOfSight(m,a,b){const d=distance(a,b),steps=Math.min(36,Math.max(5,Math.ceil(d/320)));for(let i=1;i<steps;i++){const t=i/steps;if(lerp(a.y,b.y,t)<Math.max(0,terrainHeight(m,lerp(a.x,b.x,t),lerp(a.z,b.z,t)))+8)return false;}return true;}
export function segmentDistance(a,b,p){const x=b.x-a.x,y=b.y-a.y,z=b.z-a.z,t=clamp(((p.x-a.x)*x+(p.y-a.y)*y+(p.z-a.z)*z)/(x*x+y*y+z*z||1),0,1);return Math.hypot(p.x-a.x-x*t,p.y-a.y-y*t,p.z-a.z-z*t);}

export class Simulation{
 constructor(mapIndex=0,difficulty='normal'){
  this.map=MAPS[mapIndex];this.aircraft=AIRCRAFT[0];this.difficulty=difficulty;this.time=0;this.status='flying';this.events=[];this.entities=[];this.projectiles=[];this.nextId=1;this.kills=0;this.score=0;this.warningTimes={};this.selected=null;this.lock=0;this.mode='ground';this.gunTimer=0;this.missileTimer=0;this.flareTimer=0;this.flareActive=0;this.supplyTimer=0;this.outside=0;this.scanTimer=0;this.contacts=[];this.baseDestroyed=0;
  const m=this.map,s=m.start,first=m.bases[0];this.player={x:s.x,y:Math.max(terrainHeight(m,s.x,s.z),terrainHeight(m,first.x,first.z))+1100,z:s.z,yaw:Math.atan2(first.x-s.x,first.z-s.z),pitch:0,roll:0,speed:285,throttle:.58,health:100,missiles:this.aircraft.missiles,gun:this.aircraft.gun,flares:this.aircraft.flares,g:1};
  this.bases=m.bases.map((b,i)=>({...b,id:i+1,name:['АЛЬФА','БРАВО','ДЕЛЬТА'][i],y:terrainHeight(m,b.x,b.z),destroyed:false,spawn:22+i*8}));
  for(const b of this.bases){this.addEntity('hq',b,b.x,b.z,0,190);this.addEntity('radar',b,b.x-185,b.z-145,0,70);this.addEntity('sam',b,b.x+200,b.z+100,0,85);this.addEntity('aa',b,b.x-190,b.z+160,0,60);}
  this.resupply={x:s.x,y:terrainHeight(m,s.x,s.z)+350,z:s.z};this.emit('voice','ready');
 }
 emit(type,data){this.events.push({type,data,time:this.time});}
 drain(){return this.events.splice(0);}
 warn(key,cooldown=9){if(this.time-(this.warningTimes[key]??-100)>cooldown){this.warningTimes[key]=this.time;this.emit('voice',key);}}
 addEntity(type,base,x,z,alt=0,hp=60){const e={id:this.nextId++,type,base:base.id,x,y:terrainHeight(this.map,x,z)+(alt||16),z,hp,maxHp:hp,alive:true,yaw:0,pitch:0,roll:0,age:0,cooldown:5+base.id*2,air:alt>0,radius:type==='hq'?55:type==='radar'?26:alt>0?23:22};this.entities.push(e);return e;}
 get target(){return this.entities.find(e=>e.id===this.selected&&e.alive)||null;}
 get altitude(){return this.player.y-Math.max(0,terrainHeight(this.map,this.player.x,this.player.z));}
 radar(){const p=this.player;this.contacts=this.entities.filter(e=>e.alive&&distance(p,e)<10500&&lineOfSight(this.map,p,e));return this.contacts;}
 candidates(){const p=this.player;return this.contacts.filter(e=>this.mode==='air'?e.air:!e.air).sort((a,b)=>distance(p,a)-distance(p,b));}
 selectNext(){const c=this.candidates();if(!c.length){this.selected=null;this.lock=0;return;}const i=c.findIndex(e=>e.id===this.selected);this.selected=c[(i+1)%c.length].id;this.lock=0;}
 toggleMode(){this.mode=this.mode==='ground'?'air':'ground';this.selected=null;this.selectNext();}
 canLock(t){if(!t||!this.contacts.includes(t))return false;const p=this.player,d=distance(p,t),f=forward(p.yaw,p.pitch),dot=((t.x-p.x)*f.x+(t.y-p.y)*f.y+(t.z-p.z)*f.z)/(d||1);return d<8200&&dot>.82;}
 launch(){const t=this.target,p=this.player;if(p.missiles<=0){this.warn('empty',4);return false;}if(!['flying','returning'].includes(this.status)||this.missileTimer>0||!t||this.lock<1)return false;p.missiles--;this.missileTimer=1.1;const f=forward(p.yaw,p.pitch);this.projectiles.push({id:this.nextId++,kind:'missile',enemy:false,target:t.id,x:p.x+f.x*18,y:p.y+f.y*18-3,z:p.z+f.z*18,vx:f.x*560,vy:f.y*560,vz:f.z*560,age:0,life:19});this.emit('launch',{...p});return true;}
 flares(){if(!['flying','returning'].includes(this.status)||this.player.flares<=0||this.flareTimer>0)return false;this.player.flares=Math.max(0,this.player.flares-4);this.flareTimer=4;this.flareActive=3;for(const r of this.projectiles)if(r.enemy&&r.kind==='missile'&&distance(r,this.player)<3200){r.decoy={x:this.player.x-100,y:this.player.y-140,z:this.player.z-100};}this.emit('flares',{...this.player});this.warn('flares',5);return true;}
 shoot(){const p=this.player;if(p.gun<=0||this.gunTimer>0||!['flying','returning'].includes(this.status))return;this.gunTimer=.095;p.gun--;const f=forward(p.yaw,p.pitch),to={x:p.x+f.x*1800,y:p.y+f.y*1800,z:p.z+f.z*1800};let hit=null,nearest=1900;for(const e of this.entities)if(e.alive){const d=distance(p,e);if(d<nearest&&segmentDistance(p,to,e)<e.radius+8&&lineOfSight(this.map,p,e)){hit=e;nearest=d;}}
  if(hit)this.damageEntity(hit,8);this.emit('gun',{from:{...p},to:hit?{...hit}:to,enemy:false});
 }
 damageEntity(e,n){if(!e.alive)return;e.hp-=n;this.emit('hit',{...e});if(e.hp<=0){e.alive=false;this.kills++;this.score+=e.type==='hq'?600:e.air?250:120;this.emit('explosion',{...e,size:e.type==='hq'?100:e.air?30:45});this.warn('destroyed',2);if(e.id===this.selected){this.selected=null;this.lock=0;}if(e.type==='hq'){this.bases.find(b=>b.id===e.base).destroyed=true;this.baseDestroyed++;if(this.baseDestroyed===this.bases.length){this.status='returning';this.emit('mission','return');this.warn('win',1);}}}}
 damagePlayer(n){if(this.status!=='flying'&&this.status!=='returning')return;this.player.health=Math.max(0,this.player.health-n*(this.difficulty==='easy'?.52:1));this.emit('damage',n);this.warn('damage',8);if(this.player.health<=0)this.finish(false,'Самолёт потерян');}
 finish(win,reason){if(['won','lost'].includes(this.status))return;this.status=win?'won':'lost';this.emit('result',{win,reason});if(!win)this.emit('explosion',{...this.player,size:55});}
 enemyMissile(e){if(this.projectiles.filter(r=>r.enemy&&r.kind==='missile').length>=6)return;const p=this.player,d=distance(p,e),speed=e.air?510:560;this.projectiles.push({id:this.nextId++,kind:'missile',enemy:true,x:e.x,y:e.y+8,z:e.z,vx:(p.x-e.x)/d*speed,vy:(p.y-e.y)/d*speed,vz:(p.z-e.z)/d*speed,age:0,life:15});this.warn('missile',5);}
 update(dt,input={}){
  if(!['flying','returning'].includes(this.status))return;dt=clamp(dt,0,.05);this.time+=dt;const p=this.player;
  this.gunTimer-=dt;this.missileTimer-=dt;this.flareTimer-=dt;this.flareActive=Math.max(0,this.flareActive-dt);
  p.throttle=clamp(input.throttle??p.throttle,0,1);const boost=!!input.boost;
  const wantedSpeed=130+p.throttle*(boost?325:220);p.speed+=clamp(wantedSpeed-p.speed,-42*dt,32*dt);p.speed=clamp(p.speed-Math.sin(p.pitch)*18*dt,82,475);
  const targetRoll=clamp(input.x||0,-1,1)*1.15,targetPitch=clamp(-(input.y||0),-1,1)*.67;
  p.roll=lerp(p.roll,targetRoll,1-Math.exp(-3.8*dt));p.pitch=lerp(p.pitch,targetPitch,1-Math.exp(-2.5*dt));p.yaw=wrap(p.yaw+Math.tan(p.roll)*.26*dt*(280/Math.max(180,p.speed)));
  if(input.yaw)p.yaw=wrap(p.yaw+input.yaw*.3*dt);p.g=clamp(1/Math.max(.38,Math.cos(p.roll))+Math.abs(targetPitch-p.pitch)*5,1,7.5);
  const f=forward(p.yaw,p.pitch);p.x+=f.x*p.speed*dt;p.y+=f.y*p.speed*dt;p.z+=f.z*p.speed*dt;
  const alt=this.altitude;if(alt<9||p.y<5){this.finish(false,'Столкновение с поверхностью');return;}
  if(alt<180)this.warn('altitude',7);if(p.speed<125)this.warn('stall',7);
  if(Math.max(Math.abs(p.x),Math.abs(p.z))>15600){this.outside+=dt;this.warn('boundary',7);if(this.outside>16)this.finish(false,'Вы покинули район операции');}else this.outside=0;
  this.scanTimer-=dt;if(this.scanTimer<=0){this.scanTimer=.25;this.radar();if(!this.target&&this.status==='flying')this.selectNext();}
  const t=this.target,oldLock=this.lock;if(this.canLock(t))this.lock=clamp(this.lock+dt/1.55,0,1);else this.lock=Math.max(0,this.lock-dt*2);if(oldLock<1&&this.lock===1)this.warn('target',3);
  if(input.gun)this.shoot();
  for(const b of this.bases){if(b.destroyed||this.status!=='flying')continue;b.spawn-=dt;const d=distance(p,b);if(b.spawn<=0&&d<12500&&this.entities.filter(e=>e.air&&e.alive).length<7){b.spawn=65;const type=this.entities.some(e=>e.base===b.id&&e.type==='helicopter')?'fighter':'helicopter';const e=this.addEntity(type,b,b.x+500,b.z+100,type==='fighter'?1200:410,type==='fighter'?110:90);e.yaw=Math.atan2(p.x-e.x,p.z-e.z);this.warn('incoming',12);}}
  for(const e of this.entities){if(!e.alive)continue;e.age+=dt;e.cooldown-=dt;const b=this.bases.find(b=>b.id===e.base),d=distance(p,e);
   if(e.air){const fighter=e.type==='fighter',desired=wrap(Math.atan2(p.x-e.x,p.z-e.z)+(d<(fighter?900:1000)?1.6:0));const turn=clamp(wrap(desired-e.yaw),-(fighter?.52:.7)*dt,(fighter?.52:.7)*dt);e.yaw=wrap(e.yaw+turn);e.roll=lerp(e.roll,-turn/Math.max(dt,.001)*1.5,.08);const speed=fighter?290:82,aimY=fighter?p.y:terrainHeight(this.map,e.x,e.z)+440;e.pitch=clamp((aimY-e.y)/1200,-.3,.3);const v=forward(e.yaw,e.pitch);e.x+=v.x*speed*dt;e.y=Math.max(e.y+v.y*speed*dt,terrainHeight(this.map,e.x,e.z)+100);e.z+=v.z*speed*dt;
    if(d<5000&&e.cooldown<=0&&lineOfSight(this.map,e,p)){e.cooldown=fighter?15:19;this.enemyMissile(e);}
   }else if(!b.destroyed&&this.status==='flying'){
    if(e.type==='radar'&&d<5800&&lineOfSight(this.map,e,p))this.warn('lock',14);
    if(e.type==='sam'&&d<5100&&e.cooldown<=0&&lineOfSight(this.map,e,p)){e.cooldown=20;this.enemyMissile(e);}
    if(e.type==='aa'&&d<1750&&e.cooldown<=0&&lineOfSight(this.map,e,p)){e.cooldown=.7;this.projectiles.push({id:this.nextId++,kind:'bullet',enemy:true,x:e.x,y:e.y+5,z:e.z,vx:(p.x-e.x)/d*880,vy:(p.y-e.y)/d*880,vz:(p.z-e.z)/d*880,life:2.5,age:0});this.emit('gun',{from:{...e},to:{x:p.x+f.x*p.speed*d/880,y:p.y,z:p.z+f.z*p.speed*d/880},enemy:true});}
   }
  }
  for(const r of this.projectiles){r.age+=dt;r.life-=dt;const old={x:r.x,y:r.y,z:r.z};if(r.kind==='missile'){
   const dest=r.enemy?(r.decoy||p):this.entities.find(e=>e.id===r.target&&e.alive);
   if(dest){const d=distance(r,dest)||1,speed=r.enemy?560:770,steer=clamp(dt*(r.enemy?1.2:3.8),0,1);r.vx=lerp(r.vx,(dest.x-r.x)/d*speed,steer);r.vy=lerp(r.vy,(dest.y-r.y)/d*speed,steer);r.vz=lerp(r.vz,(dest.z-r.z)/d*speed,steer);}
  }
   r.x+=r.vx*dt;r.y+=r.vy*dt;r.z+=r.vz*dt;
   if(r.enemy&&segmentDistance(old,r,p)<(r.kind==='missile'?30:16)){this.damagePlayer(r.kind==='missile'?24:3);r.life=0;this.emit('explosion',{...r,size:15});}
   if(!r.enemy){const target=this.entities.find(e=>e.id===r.target&&e.alive);if(target&&segmentDistance(old,r,target)<target.radius+20){this.damageEntity(target,115);r.life=0;}}
   if(r.y<Math.max(0,terrainHeight(this.map,r.x,r.z))+3){this.emit('explosion',{...r,size:18});r.life=0;}
  }
  this.projectiles=this.projectiles.filter(r=>r.life>0);
  if(distance(p,this.resupply)<700&&p.speed<350){this.supplyTimer+=dt;if(this.supplyTimer>2.5){if(this.status==='returning'){this.score+=Math.round(p.health*10);this.finish(true,'Все командные пункты уничтожены');}else{p.health=100;p.missiles=this.aircraft.missiles;p.gun=this.aircraft.gun;p.flares=this.aircraft.flares;this.warn('supply',18);}this.supplyTimer=0;}}else this.supplyTimer=0;
 }
}
