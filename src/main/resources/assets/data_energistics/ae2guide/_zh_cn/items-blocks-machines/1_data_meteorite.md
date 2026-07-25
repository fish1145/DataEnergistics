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
  <Block id="ae2:damaged_budding_quartz" x="1" y="0" z="0" />
  <Block id="ae2:flawed_budding_quartz" x="2" y="0" z="0" />
  <Block id="ae2:quartz_block" x="0" y="0" z="1" />
  <Block id="ae2:flawed_budding_quartz" x="0" y="0" z="2" />
  <Block id="ae2:chipped_budding_quartz" x="1" y="0" z="1" />
  <Block id="data_energistics:budding_data_crystal_0" x="1" y="0" z="2" />
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
一种基于AE陨石的特殊变种，陨石坑以及陨石都会比正常的AE陨石要大  
陨石内部有27%的概率拥有1~2个充盈数据水晶母岩(至少我见到大部分玩家实际上挺难找到他的)  
陨石内能出现的方块在它的这个总览结构上面已经显示了(如上图)  

>⚠他将作为你本模组的开端
>

---

## 数位陨石

| 陨石 | 基础掉落 | 额外产出 |
|---|-|---|
| <ItemImage id="ae2:sky_stone_block" /> | 100% <ItemImage id="ae2:sky_stone_block" /> | 无 |
| <ItemImage id="data_energistics:data_meteorite_0" /> | 100% <ItemImage id="ae2:sky_stone_block" /> 10% + 时运每级 +3% <ItemImage id="ae2:ender_dust" /> | 5% + 时运每级 +3%：生成 1 个即散数据实体 |
| <ItemImage id="data_energistics:data_meteorite_1" /> | 100% <ItemImage id="ae2:sky_stone_block" /> 20% + 时运每级 +3% <ItemImage id="ae2:ender_dust" /> 10% + 时运每级 +3% <ItemImage id="ae2:sky_dust" /> | 10% + 时运每级 +3%：生成 1 个即散数据实体 |
| <ItemImage id="data_energistics:data_meteorite_2" /> | 100% <ItemImage id="ae2:sky_stone_block" /> 25% + 时运每级 +3% <ItemImage id="ae2:ender_dust" /> 15% + 时运每级 +3% <ItemImage id="ae2:sky_dust" /> | 15% + 时运每级 +3%：生成 1~2 个即散数据实体 15%：6³空间随机传送|

---

## 即散数据

<GameScene zoom="6" background="transparent">
  <Entity id="data_energistics:dispersing_data" data="{TextureVariant:0}" x="0" y="0" z="0" />
  <Entity id="data_energistics:dispersing_data" data="{TextureVariant:1}" x="1" y="0" z="0" />
  <Entity id="data_energistics:dispersing_data" data="{TextureVariant:2}" x="2" y="0" z="0" />
  <Entity id="data_energistics:dispersing_data" data="{TextureVariant:3}" x="3" y="0" z="0" />
  <IsometricCamera yaw="0" pitch="25" />
</GameScene>

即散数据是挖掘数位陨石（掉落概率如上图表格）与挖掘残存数据矿（必定掉落1～3个）  
相邻的即散数据会自动合并，每个实体最多容纳64份数据；数量达到64份时，实体尺寸为单份数据的4倍。
>⚠当你一分钟置之不理时，它将会消散  
> 
当他出现于世界中，你需要使用
<Row> 
    <ItemLink id="data_energistics:data_capture_ball" />
    <ItemImage id="data_energistics:data_capture_ball" />
</Row>  
    <RecipeFor id="data_energistics:data_capture_ball" />
进行捕捉,当能量耗空时它会被销毁里面的数据也会被销毁
  

你也可以制作一个
<Row>
    <ItemLink id="data_energistics:me_vacuum" />
    <ItemImage id="data_energistics:me_vacuum" />
</Row>  
    <RecipeFor id="data_energistics:me_vacuum" />
将数据捕捉球放入其中进行范围性捕捉

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
