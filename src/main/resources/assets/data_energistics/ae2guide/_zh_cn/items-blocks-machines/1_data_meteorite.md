---
navigation:
  parent: data_energistics:items-blocks-machines/0_data_energistics.md
  title: 数位化陨石
  icon: data_energistics:data_meteorite_0
  position: 1
item_ids:
- data_energistics:data_meteorite_0
- data_energistics:data_meteorite_1
- data_energistics:data_meteorite_2
- data_energistics:data_meteorite_compass
---

# 数位化陨石

<GameScene zoom="3" background="transparent">
  <Block id="data_energistics:guide_data_corrosion_liquid_display" x="3" y="0" z="4" />
  <Block id="data_energistics:guide_data_corrosion_liquid_display" x="2" y="0" z="4" />
  <Block id="data_energistics:guide_data_corrosion_liquid_display" x="1" y="0" z="4" />
  <Block id="data_energistics:guide_data_corrosion_liquid_display" x="0" y="0" z="4" />

  <Block id="data_energistics:guide_data_corrosion_liquid_display" x="4" y="0" z="0" />
  <Block id="data_energistics:guide_data_corrosion_liquid_display" x="4" y="0" z="1" />
  <Block id="data_energistics:guide_data_corrosion_liquid_display" x="4" y="0" z="2" />
  <Block id="data_energistics:guide_data_corrosion_liquid_display" x="4" y="0" z="3" />

  <Block id="ae2:sky_stone_block" x="3" y="0" z="0" />
  <Block id="data_energistics:data_meteorite_0" x="3" y="0" z="1" />
  <Block id="ae2:sky_stone_block" x="3" y="0" z="2" />
  <Block id="ae2:sky_stone_block" x="0" y="0" z="3" />
  <Block id="data_energistics:data_meteorite_1" x="1" y="0" z="3" />
  <Block id="ae2:sky_stone_block" x="2" y="0" z="3" />

  <Block id="data_energistics:budding_data_crystal_0" x="0" y="0" z="0" />
  <Block id="data_energistics:budding_data_crystal_1" x="1" y="0" z="0" />
  <Block id="data_energistics:budding_data_crystal_2" x="2" y="0" z="0" />
  <Block id="ae2:quartz_block" x="0" y="0" z="1" />
  <Block id="ae2:flawed_budding_quartz" x="0" y="0" z="2" />
  <Block id="ae2:chipped_budding_quartz" x="1" y="0" z="1" />
  <Block id="data_energistics:budding_data_crystal_3" x="1" y="0" z="2" />
  <Block id="ae2:flawless_budding_quartz" x="2" y="0" z="1" />
  <Block id="ae2:quartz_block" x="2" y="0" z="2" />


  <Block id="ae2:mysterious_cube" x="1" y="1" z="1" />
  <Block id="ae2:large_quartz_bud" x="1" y="1" z="0" />
  <Block id="ae2:small_quartz_bud" x="2" y="1" z="0" />
  <Block id="ae2:quartz_cluster" x="0" y="1" z="2" />
  <Block id="ae2:large_quartz_bud" x="2" y="1" z="1" />


  <Block id="data_energistics:data_meteorite_2" x="3" y="1" z="2" />
  <Block id="ae2:quartz_block" x="2" y="2" z="2" />
  <Block id="data_energistics:budding_data_crystal_4" x="2" y="2" z="1" />
  <Block id="ae2:flawless_budding_quartz" x="1" y="2" z="2" />

  <IsometricCamera yaw="150" pitch="25" />
</GameScene>
一种基于 AE 陨石的特殊变种，陨石本体与陨石坑都比普通 AE 陨石更大。核心中心的下方与上方各有 9 个母岩位置，共 18 个位置；每个位置都会相互独立地抽取一种母岩。

| 母岩 | 每个位置的概率 |
|---|---:|
| <ItemImage id="ae2:damaged_budding_quartz" /> 损坏赛特斯母岩 | 13.75% |
| <ItemImage id="ae2:chipped_budding_quartz" /> 开裂赛特斯母岩 | 13.75% |
| <ItemImage id="ae2:flawed_budding_quartz" /> 有瑕赛特斯母岩 | 13.75% |
| <ItemImage id="ae2:flawless_budding_quartz" /> 无瑕赛特斯母岩 | 13.75% |
| <ItemImage id="data_energistics:budding_data_crystal_0" /> 失活数据水晶母岩 | 11.75% |
| <ItemImage id="data_energistics:budding_data_crystal_1" /> 无能数据水晶母岩 | 10.75% |
| <ItemImage id="data_energistics:budding_data_crystal_2" /> 疲劳数据水晶母岩 | 9.75% |
| <ItemImage id="data_energistics:budding_data_crystal_3" /> 欠亏数据水晶母岩 | 8.75% |
| <ItemImage id="data_energistics:budding_data_crystal_4" /> 充盈数据水晶母岩 | 4.00% |

数据水晶母岩的品质越高，生成概率越低。只有下方母岩为赛特斯母岩时，核心中层才可能生成现有的随机赛特斯晶芽；下方为数据水晶母岩时，中层保持为空。新分布仅影响新生成的数位化陨石。

使用精准采集挖掘时，五档数据水晶母岩都会掉落自身。未使用精准采集时，仍沿用原有的降级规则：

| 母岩 | 普通采集掉落 |
|---|---|
| <ItemImage id="data_energistics:budding_data_crystal_0" /> 失活 | 无掉落 |
| <ItemImage id="data_energistics:budding_data_crystal_1" /> 无能 | <ItemImage id="data_energistics:budding_data_crystal_0" /> 失活 |
| <ItemImage id="data_energistics:budding_data_crystal_2" /> 疲劳 | <ItemImage id="data_energistics:budding_data_crystal_1" /> 无能 |
| <ItemImage id="data_energistics:budding_data_crystal_3" /> 欠亏 | <ItemImage id="data_energistics:budding_data_crystal_2" /> 疲劳 |
| <ItemImage id="data_energistics:budding_data_crystal_4" /> 充盈 | <ItemImage id="data_energistics:budding_data_crystal_2" /> 疲劳 |

>⚠他将作为你本模组的开端
>

---

## 数位陨石

| 陨石 | 基础掉落 | 额外产出 |
|---|-|---|
| <ItemImage id="ae2:sky_stone_block" /> | 100% <ItemImage id="ae2:sky_stone_block" /> | 无 |
| <ItemImage id="data_energistics:data_meteorite_0" /> | 100% <ItemImage id="ae2:sky_stone_block" /> 10% + 时运每级 +3% <ItemImage id="ae2:ender_dust" /> | 5% + 时运每级 +3%：生成 1 个即散显性二进制实体 |
| <ItemImage id="data_energistics:data_meteorite_1" /> | 100% <ItemImage id="ae2:sky_stone_block" /> 20% + 时运每级 +3% <ItemImage id="ae2:ender_dust" /> 10% + 时运每级 +3% <ItemImage id="ae2:sky_dust" /> | 10% + 时运每级 +3%：生成 1 个即散显性二进制实体 |
| <ItemImage id="data_energistics:data_meteorite_2" /> | 100% <ItemImage id="ae2:sky_stone_block" /> 25% + 时运每级 +3% <ItemImage id="ae2:ender_dust" /> 15% + 时运每级 +3% <ItemImage id="ae2:sky_dust" /> | 15% + 时运每级 +3%：生成 1~2 个即散显性二进制实体 15%：6³空间随机传送|

---

## 即散显性二进制

<GameScene zoom="6" background="transparent">
  <Entity id="data_energistics:dispersing_data" data="{TextureVariant:0}" x="0" y="0" z="0" />
  <Entity id="data_energistics:dispersing_data" data="{TextureVariant:1}" x="1" y="0" z="0" />
  <Entity id="data_energistics:dispersing_data" data="{TextureVariant:2}" x="2" y="0" z="0" />
  <Entity id="data_energistics:dispersing_data" data="{TextureVariant:3}" x="3" y="0" z="0" />
  <IsometricCamera yaw="0" pitch="25" />
</GameScene>

即散显性二进制是挖掘数位陨石（掉落概率如上图表格）与挖掘残存数据矿（必定掉落1～3个）
附近的即散显性二进制会互相吸引并自动合并，名称会显示包含的数据量，每个实体最多容纳16份数据；数量达到16份时，实体尺寸约为单份数据的2.52倍。
>⚠当你一分钟置之不理时，它将会消散  
> 
当他出现于世界中，你需要使用
<Row> 
    <ItemLink id="data_energistics:radix_containment_sphere" />
    <ItemImage id="data_energistics:radix_containment_sphere" />
</Row>  
    <RecipeFor id="data_energistics:radix_containment_sphere" />
进行捕捉,当能量耗空时它会被销毁里面的数据也会被销毁
  

你也可以制作一个
<Row>
    <ItemLink id="data_energistics:me_vacuum" />
    <ItemImage id="data_energistics:me_vacuum" />
</Row>  
    <RecipeFor id="data_energistics:me_vacuum" />
将进制收容球体放入其中进行范围性捕捉

---

## 数蚀液
<GameScene zoom="3" background="transparent">
    <Block id="data_energistics:guide_data_corrosion_liquid_display" x="3" y="0" z="4" />
  <IsometricCamera yaw="150" pitch="25" />
</GameScene>

伴随变种陨石而来，但一般只生成在砸向平原的陨石  
  

你也可以制作一个
<Row>
    <ItemLink id="data_energistics:me_vacuum" />
    <ItemImage id="data_energistics:me_vacuum" />
</Row>  
    <RecipeFor id="data_energistics:me_vacuum" />
将装载流体磁盘进行范围性吸取  
>⚠它有约等于两倍龙息的伤害，请小心行事
>

## 数位化陨石罗盘  

>经过特殊化处理(合成)过后的陨石罗盘，专门用于寻找本模组陨石
> 使用方法与普通的陨石罗盘一致

<Row>
    <ItemImage id="data_energistics:data_meteorite_compass" scale="6" />
    <RecipeFor id="data_energistics:data_meteorite_compass" />
</Row>
