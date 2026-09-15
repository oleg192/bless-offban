import {build} from 'esbuild';
import {mkdir,copyFile,cp,rm} from 'node:fs/promises';
await build({entryPoints:['web/game.js'],bundle:true,minify:true,format:'iife',target:['chrome90'],outfile:'web/game.bundle.js',legalComments:'eof'});
const target='app/src/main/assets/game';
await rm(target,{recursive:true,force:true});await mkdir(target,{recursive:true});
for(const name of ['index.html','style.css','game.bundle.js'])await copyFile('web/'+name,target+'/'+name);
await cp('web/audio',target+'/audio',{recursive:true});
await copyFile('node_modules/three/LICENSE',target+'/THREE-LICENSE.txt');
console.log('Bundled all game code and voice recordings for offline use');
