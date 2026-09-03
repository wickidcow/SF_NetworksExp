const catalogGrid=document.querySelector('#catalog-grid');
const searchInput=document.querySelector('#catalog-search');
const filtersEl=document.querySelector('#filters');
const resultCount=document.querySelector('#result-count');
const catalogCount=document.querySelector('#catalog-count');
const emptyState=document.querySelector('#catalog-empty');
const clearFilters=document.querySelector('#clear-filters');
const featureDialog=document.querySelector('#feature-dialog');
const itemDialog=document.querySelector('#item-dialog');
let items=[];
let activeFilter='All';

const filters=['All','Networks','Expansion','Network','Storage','Logistics','Crafting','Power','Wireless','Tools'];

const featured={
  controller:{badge:'CORE NETWORK',title:'Controller',lead:'The Controller is the root of a Networks system. Other nodes become useful when they are part of a valid network rooted at a Controller.',keywords:['CONTROLLER','BRIDGE','CONTROL_X','CONTROL_V'],html:`<div class="guide-flow"><div><b>1. Place the root</b><span>Start with one Controller for the network you are building.</span></div><div><b>2. Connect nodes</b><span>Use Bridges and functional network blocks to extend the connected system.</span></div><div><b>3. Verify state</b><span>If nodes behave oddly after chunk changes, use Networks Doctor before rebuilding blindly.</span></div></div><div class="guide-list"><div class="guide-row"><span>✓</span><div><b>Existing-world safe</b><small>The Legacy fork preserves the established Networks plugin identity and placed-block IDs wherever practical.</small></div></div><div class="guide-row"><span>!</span><div><b>Chunk lifecycle matters</b><small>Controllers rebuild connected state as chunks load and unload. Avoid /reload; use a full server restart for plugin changes.</small></div></div></div>`},
  storage:{badge:'STORAGE',title:'Choose the storage behind your network',lead:'Networks can expose several storage styles through one interface. Pick based on capacity, upgrade path, and the integrations installed on your server.',keywords:['CELL','QUANTUM_STORAGE','DRAWER','CARGO_STORAGE','STORAGE_CARD','STORAGE_UNIT'],html:`<div class="guide-flow"><div><b>Network Cell</b><span>Classic Networks storage for a straightforward network-backed inventory.</span></div><div><b>Quantum Storage</b><span>High-capacity tiered storage, including additional Expansion tiers.</span></div><div><b>Drawers & adapters</b><span>Expansion managers and optional adapters extend storage workflows without replacing the core network.</span></div></div><div class="guide-list"><div class="guide-row"><span>∞</span><div><b>InfinityExpansion2 support</b><small>The Legacy fork includes a fail-soft IE2 storage adapter. If IE2 is absent or incompatible, that optional integration can disable without taking Networks down.</small></div></div><div class="guide-row"><span>13</span><div><b>Cargo Storage Units</b><small>Networks Expansion adds a large cargo-storage tier family, all indexed in the live catalog below.</small></div></div></div>`},
  grids:{badge:'ACCESS',title:'Grids: one view of your storage',lead:'A Network Grid gives players one place to see and move network items. Crafting and new-style variants layer more interaction on the same idea.',keywords:['GRID','HANGING_GRID'],html:`<div class="guide-flow"><div><b>Grid</b><span>Browse, search, deposit, and withdraw items available to the network.</span></div><div><b>Crafting Grid</b><span>Add a crafting interface while drawing ingredients from connected storage.</span></div><div><b>Expansion grids</b><span>New-style, hanging, and smart Crafting Grid variants expand how the interface can be used.</span></div></div>`},
  movement:{badge:'LOGISTICS',title:'Move items without hand-sorting',lead:'Classic Networks gives you Pushers, Grabbers, Importers and Exporters. Networks Expansion adds full transfer families, advanced variants, line transfers, smart nodes, and quick tools.',keywords:['PUSHER','GRABBER','IMPORT','EXPORT','TRANSFER','SMART_','VACUUM','GREEDY','PURGER','TRASH'],html:`<div class="guide-flow"><div><b>Pusher</b><span>Moves network items toward a configured destination or compatible machine input.</span></div><div><b>Grabber</b><span>Pulls items from a configured source into network-accessible storage.</span></div><div><b>Transfer families</b><span>Expansion adds basic, advanced, line, vanilla-aware, whitelist, More and Best transfer variants.</span></div></div><div class="guide-list"><div class="guide-row"><span>⇄</span><div><b>Start simple</b><small>Use the basic node that matches the job, then step up to advanced/smart variants when you actually need their behavior.</small></div></div><div class="guide-row"><span>↺</span><div><b>Legacy safety work</b><small>The maintained fork adds rollback/compensation diagnostics around item transfers to reduce silent loss during failed operations.</small></div></div></div>`},
  crafting:{badge:'CRAFTING',title:'Craft from storage, then automate it',lead:'Networks combines Crafting Grids, recipe encoders, blueprints and automatic crafters. Networks Expansion broadens this into many supported Slimefun crafting systems.',keywords:['CRAFTER','CRAFTING_BLUEPRINT','RECIPE_ENCODER','BLUEPRINT','WORKBENCH'],html:`<div class="guide-flow"><div><b>Encode a recipe</b><span>Use the appropriate recipe encoder to create a blueprint for a supported crafting system.</span></div><div><b>Load the blueprint</b><span>Automatic crafters read the encoded recipe and try to source ingredients through the network.</span></div><div><b>Choose behavior</b><span>Withholding variants stop producing once their output buffer is full instead of continuously crafting.</span></div></div><div class="guide-list"><div class="guide-row"><span>64</span><div><b>Classic auto crafter</b><small>The historical Networks behavior consumes network power per craft; the withholding variant is intentionally more expensive.</small></div></div><div class="guide-row"><span>✦</span><div><b>Expansion recipe families</b><small>Expansion includes advanced autocrafters/encoders for multiple Slimefun crafting stations such as the Ancient Altar, Armor Forge, Compressor, Grind Stone, Juicer, Magic Workbench, Ore Crusher, Pressure Chamber, Smeltery and more.</small></div></div></div>`},
  wireless:{badge:'WIRELESS',title:'Connect or access a network wirelessly',lead:'Wireless tools let you pair network locations and bridge access without requiring every interaction to happen at the same physical block.',keywords:['WIRELESS','REMOTE'],html:`<div class="guide-flow"><div><b>Receiver</b><span>Represents the target side/location for a wireless connection.</span></div><div><b>Configurator</b><span>Store a receiver location, then apply that pairing to the compatible transmitter.</span></div><div><b>Remote</b><span>Remote tiers provide player-facing network access subject to the item’s range/validation rules.</span></div></div><div class="guide-list"><div class="guide-row"><span>⌁</span><div><b>Historical pairing flow</b><small>Right-clicking a receiver with the Wireless Configurator stores its location; shift-right-clicking a transmitter applies the stored configuration.</small></div></div></div>`},
  power:{badge:'POWER',title:'Power the network',lead:'Power outlets and capacitor tiers supply or buffer energy used by powered network functions such as automated crafting.',keywords:['CAPACITOR','POWER_OUTLET','POWER_DISPLAY','LINE_POWER'],html:`<div class="guide-flow"><div><b>Power Outlet</b><span>Connects the network to an available Slimefun energy source.</span></div><div><b>Capacitors</b><span>Classic tiers are joined by higher Expansion capacitor tiers in the registered catalog.</span></div><div><b>Power Display</b><span>Use the display to make available network energy visible while tuning automation.</span></div></div>`},
  expansion:{badge:'NETWORKS EXPANSION',title:'Everything the old wiki did not cover',lead:'The current fork contains the full Networks Expansion feature set in addition to classic Networks. This site indexes it from the plugin’s own compatibility contract.',keywords:['NTW_EXPANSION_'],html:`<div class="guide-list"><div class="guide-row"><span>⇄</span><div><b>Advanced & line transfer families</b><small>Pushers, Grabbers, whitelist and vanilla variants, plus More/Best tiers and advanced line-transfer equivalents.</small></div></div><div class="guide-row"><span>▦</span><div><b>Storage expansion</b><small>Drawer/Quantum managers, cargo storage tiers, additional quantum storage, converters and upgrade tooling.</small></div></div><div class="guide-row"><span>⌘</span><div><b>Interface expansion</b><small>New-style Grid, Crafting Grid, Hanging Grid, Smart Crafting Grid, Switching Monitor and specialized monitor variants.</small></div></div><div class="guide-row"><span>✦</span><div><b>Automation expansion</b><small>Advanced autocrafters, recipe encoders/blueprints, Due Machine, Smart Pusher/Grabber, Offsetter, Super Trash and other utility machines.</small></div></div></div>`},
  doctor:{badge:'DIAGNOSTICS',title:'Networks Doctor',lead:'The maintained fork includes /networks doctor diagnostics for the state that matters most on established servers.',keywords:['DEBUG','INFO_TOOL','STATUS_VIEWER'],html:`<div class="guide-list"><div class="guide-row"><span>1</span><div><b>Run <code>/networks doctor scan</code></b><small>Inspect loaded databases, transfer state, controller health, registries and storage adapters without force-loading chunks.</small></div></div><div class="guide-row"><span>2</span><div><b>Read controller failures before replacing blocks</b><small>Repeated rebuild failures can be isolated and cooled down instead of repeatedly hammering the same broken controller.</small></div></div><div class="guide-row"><span>3</span><div><b>Check optional integrations</b><small>A missing optional API should disable that integration rather than disabling Networks itself.</small></div></div></div>`},
  upgrade:{badge:'SERVER OWNER',title:'Safe upgrade checklist',lead:'Networks stores real player inventory state. Treat plugin upgrades like a data migration even when no intentional schema migration is expected.',keywords:[],html:`<div class="guide-list"><div class="guide-row"><span>1</span><div><b>Back up worlds and Networks/Slimefun data</b><small>Include the existing CargoStorageUnits database and any storage-related plugin data used by your setup.</small></div></div><div class="guide-row"><span>2</span><div><b>Perform a full stop/start</b><small>Do not use /reload when changing Slimefun or Networks JARs.</small></div></div><div class="guide-row"><span>3</span><div><b>Run Doctor and test established networks</b><small>Test Controllers, Grids, storage, import/export, push/grab, wireless links, blueprints and autocrafters.</small></div></div><div class="guide-row"><span>4</span><div><b>Test chunk unload/reload and restart persistence</b><small>Compare stored amounts after a clean restart before promoting a new build to production.</small></div></div></div>`},
  compatibility:{badge:'LEGACY CONTRACT',title:'What the Legacy fork promises to preserve',lead:'The compatibility contract exists to keep established worlds upgradeable while modernizing the runtime and safety behavior.',keywords:[],html:`<div class="guide-list"><div class="guide-row"><span>✓</span><div><b>Plugin & data identity</b><small>Bukkit plugin name Networks, existing package identity, established persistent-data namespaces and CargoStorageUnits.db are retained.</small></div></div><div class="guide-row"><span>288</span><div><b>Registered item ID contract</b><small>The established 2.1.111/Alpha compatibility snapshot is preserved with no intentional item-ID or placed-block migration.</small></div></div><div class="guide-row"><span>21</span><div><b>Java compatibility</b><small>Networks release bytecode remains Java 21 while builds are verified against the maintained Slimefun core targets.</small></div></div><div class="guide-row"><span>!</span><div><b>Folia is not claimed</b><small>Multi-chunk transactional networks are not inherently region-safe, so the compatibility contract does not pretend otherwise.</small></div></div></div>`}
};

const curatedDescriptions=[
  [/CONTROLLER/,'Root controller for a connected Networks system.'],
  [/BRIDGE/,'Network bridge used to extend or connect network topology.'],
  [/CRAFTING_GRID/,'Network storage interface with crafting capabilities.'],
  [/(^|_)GRID/,'Interface for browsing and moving items in network storage.'],
  [/CELL/,'Classic Networks storage cell.'],
  [/QUANTUM_STORAGE/,'High-capacity tiered storage available to Networks.'],
  [/DRAWER/,'Networks Expansion drawer storage or drawer-management component.'],
  [/CARGO_STORAGE/,'Networks Expansion cargo storage unit or related model.'],
  [/BESTPUSHER/,'High-tier pusher for moving items from network storage.'],
  [/MOREPUSHER/,'Upgraded pusher for higher-capability network item movement.'],
  [/SMART_PUSHER/,'Networks Expansion smart pusher for more selective item movement.'],
  [/PUSHER/,'Moves items from the network toward a configured destination.'],
  [/SMART_GRABBER/,'Networks Expansion smart grabber for more selective item intake.'],
  [/GRABBER/,'Pulls items from a configured source into network-accessible storage.'],
  [/IMPORT/,'Imports items from an attached inventory or compatible system.'],
  [/EXPORT/,'Exports network items toward an attached inventory or compatible system.'],
  [/TRANSFER/,'Networks Expansion transfer component for cargo-style item movement.'],
  [/WIRELESS_TRANSMITTER/,'Transmitter side of a paired wireless network connection.'],
  [/WIRELESS_RECEIVER/,'Receiver/target side of a wireless network connection.'],
  [/WIRELESS_CONFIGURATOR/,'Tool used to pair compatible wireless network blocks.'],
  [/REMOTE/,'Portable remote access to Networks storage, subject to tier/range rules.'],
  [/AUTO_CRAFTER/,'Automatically crafts an encoded recipe using materials available to the network.'],
  [/RECIPE_ENCODER/,'Encodes a supported recipe into a blueprint for network automation.'],
  [/BLUEPRINT/,'Encoded recipe or blueprint component used by Networks crafting automation.'],
  [/MANAGER/,'Networks Expansion management block for coordinating a related storage or crafting system.'],
  [/CAPACITOR/,'Network energy storage/buffer tier.'],
  [/POWER_OUTLET/,'Feeds available Slimefun energy into a network.'],
  [/POWER_DISPLAY/,'Displays network power information.'],
  [/VACUUM/,'Collects compatible nearby items into the network workflow.'],
  [/GREEDY/,'Aggressive intake/storage behavior for matching network workflows.'],
  [/PURGER|TRASH/,'Removes or discards configured items from a network workflow.'],
  [/MONITOR/,'Network monitor for viewing or controlling a specialized flow/state.'],
  [/CONFIGURATOR/,'Configuration tool for compatible Networks blocks.'],
  [/MOVER/,'Utility for moving items or configuration between compatible components.'],
  [/VIEWER/,'Utility for inspecting item flow or machine state.'],
  [/OFFSETTER/,'Networks Expansion utility for offset-based machine/network behavior.'],
  [/DUE_MACHINE/,'Networks Expansion Due Machine automation component.'],
  [/STORAGE_CARD/,'Storage card conversion or management component.'],
  [/UPGRADE_TABLE/,'Upgrade interface for compatible Networks Expansion storage.']
];

function cleanName(id){
  return id.replace(/^NTW_EXPANSION_/,'').replace(/^NTW_/,'').split('_').map(part=>{
    if(/^\d+$/.test(part)) return part;
    const map={AI:'AI',IE2:'IE2',GUI:'GUI',NBT:'NBT',XP:'XP'};
    return map[part]||part.charAt(0)+part.slice(1).toLowerCase();
  }).join(' ');
}
function familyFor(id){return id.startsWith('NTW_EXPANSION_')?'Expansion':'Networks'}
function categoryFor(id){
  const s=id.replace(/^NTW_EXPANSION_/,'').replace(/^NTW_/,'');
  if(/WIRELESS|REMOTE/.test(s))return'Wireless';
  if(/CAPACITOR|POWER|ENERGY/.test(s))return'Power';
  if(/CRAFTER|CRAFTING|BLUEPRINT|RECIPE|WORKBENCH|ENCODER/.test(s))return'Crafting';
  if(/TRANSFER|PUSHER|GRABBER|IMPORT|EXPORT|VACUUM|GREEDY|PURGER|TRASH|ITEM_MOVER/.test(s))return'Logistics';
  if(/STORAGE|DRAWER|CELL|QUANTUM|BARREL/.test(s))return'Storage';
  if(/TOOL|CONFIGURATOR|VIEWER|PRESETTER|DIFFERENTER|CRAYON|RAKE|PROBE|DEBUG|AXE/.test(s))return'Tools';
  return'Network';
}
function glyphFor(category){return({Network:'N',Storage:'▦',Logistics:'⇄',Crafting:'◇',Power:'ϟ',Wireless:'⌁',Tools:'⌘'})[category]||'N'}
function descriptionFor(id,category,family){
  const match=curatedDescriptions.find(([re])=>re.test(id));
  if(match)return match[1];
  return `${family} registered ${category.toLowerCase()} component. Open this item in the Slimefun guide for its current recipe and in-game lore.`;
}
function makeItem(id){const family=familyFor(id),category=categoryFor(id);return{id,name:cleanName(id),family,category,glyph:glyphFor(category),description:descriptionFor(id,category,family)}}

function buildFilters(){
  filtersEl.innerHTML='';
  filters.forEach(name=>{const b=document.createElement('button');b.type='button';b.className='filter-chip'+(name===activeFilter?' active':'');b.textContent=name;b.dataset.filter=name;b.addEventListener('click',()=>{activeFilter=name;buildFilters();renderCatalog()});filtersEl.appendChild(b)});
}
function filteredItems(){
  const q=searchInput.value.trim().toLowerCase();
  return items.filter(item=>{
    const filterMatch=activeFilter==='All'||item.family===activeFilter||item.category===activeFilter;
    const searchMatch=!q||`${item.name} ${item.id} ${item.family} ${item.category} ${item.description}`.toLowerCase().includes(q);
    return filterMatch&&searchMatch;
  });
}
function renderCatalog(){
  const shown=filteredItems();catalogGrid.innerHTML='';
  shown.forEach(item=>{const card=document.createElement('button');card.type='button';card.className='catalog-card '+(item.family==='Expansion'?'expansion':'');card.innerHTML=`<div class="catalog-top"><span class="catalog-glyph">${item.glyph}</span><span class="family-dot">${item.family.toUpperCase()}</span></div><h3>${item.name}</h3><span class="catalog-category">${item.category}</span><code class="catalog-id">${item.id}</code>`;card.addEventListener('click',()=>openItem(item));catalogGrid.appendChild(card)});
  resultCount.textContent=`Showing ${shown.length} of ${items.length} registered IDs`;
  emptyState.hidden=shown.length!==0;
}
function openItem(item){
  document.querySelector('#item-glyph').textContent=item.glyph;
  document.querySelector('#item-family').textContent=item.family.toUpperCase();
  document.querySelector('#item-name').textContent=item.name;
  document.querySelector('#item-category').textContent=item.category;
  document.querySelector('#item-id').textContent=item.id;
  document.querySelector('#item-description').textContent=item.description;
  const tips=document.querySelector('#item-tips');
  tips.innerHTML=`<div class="item-tip"><b>Source of truth:</b> this entry comes from the repository’s preserved 2.1.111 item-ID compatibility snapshot.</div><div class="item-tip"><b>Recipe:</b> use the in-game Slimefun guide for the exact recipe on your installed build; this visual wiki intentionally avoids duplicating recipes that can drift between releases.</div>`;
  itemDialog.showModal();
}
function openFeature(key){
  const f=featured[key];if(!f)return;
  document.querySelector('#dialog-badge').textContent=f.badge;
  document.querySelector('#dialog-title').textContent=f.title;
  document.querySelector('#dialog-lead').textContent=f.lead;
  document.querySelector('#dialog-content').innerHTML=f.html;
  const related=document.querySelector('#dialog-catalog');related.innerHTML='';
  if(f.keywords.length){
    const matches=items.filter(i=>f.keywords.some(k=>k==='NTW_EXPANSION_'?i.id.startsWith(k):i.id.includes(k))).slice(0,28);
    matches.forEach(item=>{const b=document.createElement('button');b.type='button';b.textContent=item.name;b.addEventListener('click',()=>{featureDialog.close();openItem(item)});related.appendChild(b)});
  }
  featureDialog.showModal();
}

async function loadCatalog(){
  try{
    const response=await fetch('assets/data/item-ids.txt',{cache:'no-store'});
    if(!response.ok)throw new Error('catalog unavailable');
    const text=await response.text();
    const ids=[...new Set(text.split(/\r?\n/).map(s=>s.trim()).filter(Boolean))];
    items=ids.map(makeItem);
  }catch(error){
    const fallback=['NTW_CONTROLLER','NTW_BRIDGE','NTW_GRID','NTW_CRAFTING_GRID','NTW_CELL','NTW_PUSHER','NTW_GRABBER','NTW_IMPORT','NTW_EXPORT','NTW_NETWORK_WIRELESS_TRANSMITTER','NTW_NETWORK_WIRELESS_RECEIVER','NTW_AUTO_CRAFTER','NTW_RECIPE_ENCODER','NTW_QUANTUM_STORAGE_1','NTW_EXPANSION_SMART_PUSHER','NTW_EXPANSION_SMART_GRABBER','NTW_EXPANSION_CRAFTER_MANAGER','NTW_EXPANSION_DRAWER_MANAGER','NTW_EXPANSION_QUANTUM_MANAGER','NTW_EXPANSION_CARGO_STORAGE_UNIT_13'];
    items=fallback.map(makeItem);
  }
  catalogCount.textContent=items.length;
  buildFilters();renderCatalog();
}

searchInput.addEventListener('input',renderCatalog);
clearFilters.addEventListener('click',()=>{searchInput.value='';activeFilter='All';buildFilters();renderCatalog()});
document.querySelectorAll('[data-feature]').forEach(el=>el.addEventListener('click',()=>openFeature(el.dataset.feature)));
document.querySelectorAll('[data-preset-filter]').forEach(el=>el.addEventListener('click',()=>{activeFilter=el.dataset.presetFilter;searchInput.value='';buildFilters();setTimeout(renderCatalog,0)}));
document.querySelector('#dialog-close').addEventListener('click',()=>featureDialog.close());
document.querySelector('.item-close').addEventListener('click',()=>itemDialog.close());
[featureDialog,itemDialog].forEach(dialog=>dialog.addEventListener('click',event=>{const r=dialog.getBoundingClientRect();if(event.clientX<r.left||event.clientX>r.right||event.clientY<r.top||event.clientY>r.bottom)dialog.close()}));

loadCatalog();
