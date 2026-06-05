---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: 数据传送锚
  icon: data_energistics:data_teleport_anchor
  position: 9
item_ids:
- data_energistics:data_teleport_anchor
---

## 数据传送锚
百亿光年之外，点对点的数据传送，把两颗孤独的文明，钉成了彼此的锚点，永不分离。

<GameScene zoom="6" background="transparent">
    <Block id="data_energistics:data_teleport_anchor" x="0" y="0" z="0" />
   <IsometricCamera yaw="25" pitch="25" />
</GameScene>
<Row>
  <RecipeFor id="data_energistics:data_teleport_anchor" />
</Row>

一个点对点的传送。消耗10 kAE  
共有16个颜色，颜色与颜色之间互相隔离。使用<ItemImage id="data_energistics:data_crystal_cutting_knife" />可以无视颜色

<GameScene zoom="4.2" background="transparent">
    <Block id="data_energistics:data_teleport_anchor" p:lit="true" p:color="black" x="0" y="0" z="0" />
    <Block id="data_energistics:data_teleport_anchor" p:lit="true" p:color="blue" x="1" y="0" z="0" />
    <Block id="data_energistics:data_teleport_anchor" p:lit="true" p:color="brown" x="2" y="0" z="0" />
    <Block id="data_energistics:data_teleport_anchor" p:lit="true" p:color="cyan" x="3" y="0" z="0" />
    <Block id="data_energistics:data_teleport_anchor" p:lit="true" p:color="gray" x="4" y="0" z="0" />
    <Block id="data_energistics:data_teleport_anchor" p:lit="true" p:color="green" x="5" y="0" z="0" />
    <Block id="data_energistics:data_teleport_anchor" p:lit="true" p:color="light_blue" x="0" y="0" z="1" />
    <Block id="data_energistics:data_teleport_anchor" p:lit="true" p:color="light_gray" x="1" y="0" z="1" />
    <Block id="data_energistics:data_teleport_anchor" p:lit="true" p:color="lime" x="2" y="0" z="1" />
    <Block id="data_energistics:data_teleport_anchor" p:lit="true" p:color="magenta" x="3" y="0" z="1" />
    <Block id="data_energistics:data_teleport_anchor" p:lit="true" p:color="orange" x="4" y="0" z="1" />
    <Block id="data_energistics:data_teleport_anchor" p:lit="true" p:color="pink" x="5" y="0" z="1" />
    <Block id="data_energistics:data_teleport_anchor" p:lit="true" p:color="purple" x="1" y="0" z="2" />
    <Block id="data_energistics:data_teleport_anchor" p:lit="true" p:color="red" x="2" y="0" z="2" />
    <Block id="data_energistics:data_teleport_anchor" p:lit="true" p:color="white" x="3" y="0" z="2" />
    <Block id="data_energistics:data_teleport_anchor" p:lit="true" p:color="yellow" x="4" y="0" z="2" />
    <IsometricCamera yaw="25" pitch="25" />
</GameScene>  
使用染色器右键或者用染料右键将其染色

---

# 特殊
<ItemImage id="data_energistics:data_crystal_cutting_knife" scale="6"/>  
当手持时，将自身9×9区块范围内的数据传送锚颜色对应高亮显示，右键将消耗20数据流以及400 AE进行传送至上方
