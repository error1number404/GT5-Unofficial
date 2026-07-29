package gregtech.common.tileentities.machines.multi;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.structure.error.StructureErrorRegistry.UNKNOWN_TIER;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.chainAllGlasses;
import static gregtech.api.util.GTStructureUtility.ofFrame;
import static gregtech.common.tileentities.machines.multi.MTEEMMA.EMMAElectrodeHatches.ElectrodeHatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import bartworks.system.material.WerkstoffLoader;
import goodgenerator.items.GGMaterial;
import gregtech.api.GregTechAPI;
import gregtech.api.casing.Casings;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTUtility;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import kubatech.tileentity.gregtech.hatch.MTEElectrodeDetectorHatch;
import kubatech.tileentity.gregtech.hatch.MTEElectrodeHatch;

public class MTEEMMA extends MTEExtendedPowerMultiBlockBase<MTEEMMA>
    implements ISurvivalConstructable, ICasingTextureProvider {

    private static IStructureDefinition<MTEEMMA> STRUCTURE_DEFINITION = null;

    private static final int OFFSET_X = 15;
    private static final int OFFSET_Y = 9;
    private static final int OFFSET_Z = 0;

    private static final String STRUCTURE_TIER_1 = "t1";
    private static final String STRUCTURE_TIER_2 = "t2";
    private static final String STRUCTURE_TIER_3 = "t3";
    private static final String STRUCTURE_TIER_4 = "t4";

    private static final int PARALLEL_PER_TIER = 4;
    private static final float SPEED = 2.8f;
    private static final float EU_EFFICIENCY = 0.9f;

    private static final IIconContainer TEXTURE_CONTROLLER = Textures.BlockIcons.custom("iconsets/OVERLAY_EMMA");
    private static final IIconContainer TEXTURE_CONTROLLER_GLOW = Textures.BlockIcons
        .customOptional("iconsets/OVERLAY_EMMA_GLOW");
    private static final IIconContainer TEXTURE_CONTROLLER_ACTIVE = Textures.BlockIcons
        .custom("iconsets/OVERLAY_EMMA_ACTIVE");
    private static final IIconContainer TEXTURE_CONTROLLER_ACTIVE_GLOW = Textures.BlockIcons
        .customOptional("iconsets/OVERLAY_EMMA_ACTIVE_GLOW");

    public MTEEMMA(final int aID, final String aName, final String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEEMMA(final String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(final IGregTechTileEntity aTileEntity) {
        return new MTEEMMA(this.mName);
    }

    @Override
    public IStructureDefinition<MTEEMMA> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTEEMMA>builder()
                .addShape(
                    STRUCTURE_TIER_1,
                    // spotless:off
                    transpose(new String[][]{
                        {"                           ","                           ","                           ","   D D               D D   ","   D D               D D   ","   D D               D D   ","   D D               D D   ","   D D               D D   ","                           ","                           ","                           "},
                        {"                           ","                           ","   D D               D D   ","    I                 O    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    I                 O    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    I                 O    ","   D D               D D   ","                           ","                           "},
                        {"                           ","   D D               D D   ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","   D D               D D   ","                           "},
                        {"   D D               D D   ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","    I                 O    ","    IGGGGGGGGGGGGGGGGGO    ","    I                 O    ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","   D D               D D   "},
                        {"   D D               D D   ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","    I                 O    ","    IGGGGGGGGGGGGGGGGGO    ","    I                 O    ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","   D D               D D   "},
                        {"   D D               D D   ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","    LBBBBBBBBBBBBBBBBBO    ","    IGGGGGGGGGGGGGGGGGO    ","    LBBBBBBBBBBBBBBBBBO    ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","   D D               D D   "},
                        {"   D D               D D   ","FFEEIEEEEEEED     DEEEOEE  ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","    I                 O    ","    IGGGGGGGGGGGGGGGGGO    ","    I                 O    ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","   D D               D D   "},
                        {"   D D               D D   ","    I       DFFFFFD   O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","    I                 O    ","    IGGGGGGGGGGGGGGGGGOCCCC","    I                 O    ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","   D D               D D   "},
                        {"             JJJJJ         ","   D D      DJJJJJD  D D   ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO   C","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","   D D               D D   ","                           "},
                        {"             JJ~JJ         ","            DJJJJJD        ","   D D               D D   ","    I                 O    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    I                 O   C","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    I                 O    ","   D D               D D   ","                           ","                           "},
                        {"             JJJJJCCCCCCCCC","            DJJJJJD       C","                          C","   D D               D D  C","   D D               D D  C","   D D               D D  C","   D D               D D   ","   D D               D D   ","                           ","                           ","                           "}
                    }))
                .addShape(
                    STRUCTURE_TIER_2,
                    transpose(new String[][] {
                        {"                           ","                           ","                           ","   D D               D D   ","   D D               D D   ","   D D               D D   ","   D D               D D   ","   D D               D D   ","                           ","                           ","                           "},
                        {"                           ","                           ","   D D               D D   ","    I                 O    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    I                 O    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    I                 O    ","   D D               D D   ","                           ","                           "},
                        {"                           ","   D D               D D   ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","   D D               D D   ","                           "},
                        {"   D D               D D   ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","    I                 O    ","    IGGGGGGGGGGGGGGGGGO    ","    I                 O    ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","   D D               D D   "},
                        {"   D D               D D   ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","    LKKKKKKKKKKKKKKKKKO    ","    IGGGGGGGGGGGGGGGGGO    ","    LKKKKKKKKKKKKKKKKKO    ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","   D D               D D   "},
                        {"   D D               D D   ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","    I                 O    ","    IGGGGGGGGGGGGGGGGGO    ","    I                 O    ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","   D D               D D   "},
                        {"   D D               D D   ","FFEEIEEEEEEED     DEEEOEE  ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","    LKKKKKKKKKKKKKKKKKO    ","    IGGGGGGGGGGGGGGGGGO    ","    LKKKKKKKKKKKKKKKKKO    ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","   D D               D D   "},
                        {"   D D               D D   ","    I       DFFFFFD   O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","    I                 O    ","    IGGGGGGGGGGGGGGGGGOCCCC","    I                 O    ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","   D D               D D   "},
                        {"             JJJJJ         ","   D D      DJJJJJD  D D   ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO   C","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","   D D               D D   ","                           "},
                        {"             JJ~JJ         ","            DJJJJJD        ","   D D               D D   ","    I                 O    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    I                 O   C","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    I                 O    ","   D D               D D   ","                           ","                           "},
                        {"             JJJJJCCCCCCCCC","            DJJJJJD       C","                          C","   D D               D D  C","   D D               D D  C","   D D               D D  C","   D D               D D   ","   D D               D D   ","                           ","                           ","                           "}
                    }))
                .addShape(
                    STRUCTURE_TIER_3,
                    transpose(new String[][] {
                        {"                           ","                           ","                           ","   D D               D D   ","   D D               D D   ","   D D               D D   ","   D D               D D   ","   D D               D D   ","                           ","                           ","                           "},
                        {"                           ","                           ","   D D               D D   ","    I                 O    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    I                 O    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    I                 O    ","   D D               D D   ","                           ","                           "},
                        {"                           ","   D D               D D   ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","   D D               D D   ","                           "},
                        {"   D D               D D   ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","    LMMMMMMMMMMMMMMMMMO    ","    IGGGGGGGGGGGGGGGGGO    ","    LMMMMMMMMMMMMMMMMMO    ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","   D D               D D   "},
                        {"   D D               D D   ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","    I                 O    ","    IGGGGGGGGGGGGGGGGGO    ","    I                 O    ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","   D D               D D   "},
                        {"   D D               D D   ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","    LMMMMMMMMMMMMMMMMMO    ","    I                 O    ","    IGGGGGGGGGGGGGGGGGO    ","    I                 O    ","    LMMMMMMMMMMMMMMMMMO    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","   D D               D D   "},
                        {"   D D               D D   ","FFEEIEEEEEEED     DEEEOEE  ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","    I                 O    ","    IGGGGGGGGGGGGGGGGGO    ","    I                 O    ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","   D D               D D   "},
                        {"   D D               D D   ","    I       DFFFFFD   O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","    LMMMMMMMMMMMMMMMMMO    ","    IGGGGGGGGGGGGGGGGGOCCCC","    LMMMMMMMMMMMMMMMMMO    ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","   D D               D D   "},
                        {"             JJJJJ         ","   D D      DJJJJJD  D D   ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO   C","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","   D D               D D   ","                           "},
                        {"             JJ~JJ         ","            DJJJJJD        ","   D D               D D   ","    I                 O    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    I                 O   C","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    I                 O    ","   D D               D D   ","                           ","                           "},
                        {"             JJJJJCCCCCCCCC","            DJJJJJD       C","                          C","   D D               D D  C","   D D               D D  C","   D D               D D  C","   D D               D D   ","   D D               D D   ","                           ","                           ","                           "}
                    }))
                .addShape(
                    STRUCTURE_TIER_4,
                    transpose(new String[][] {
                        {"                           ","                           ","                           ","   D D               D D   ","   D D               D D   ","   D D               D D   ","   D D               D D   ","   D D               D D   ","                           ","                           ","                           "},
                        {"                           ","                           ","   D D               D D   ","    I                 O    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    I                 O    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    I                 O    ","   D D               D D   ","                           ","                           "},
                        {"                           ","   D D               D D   ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","   D D               D D   ","                           "},
                        {"   D D               D D   ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","    LNNNNNNNNNNNNNNNNNO    ","    IGGGGGGGGGGGGGGGGGO    ","    LNNNNNNNNNNNNNNNNNO    ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","   D D               D D   "},
                        {"   D D               D D   ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    IAAAAAAAAAAAAAAAAAO    ","    LNNNNNNNNNNNNNNNNNO    ","    I                 O    ","    IGGGGGGGGGGGGGGGGGO    ","    I                 O    ","    LNNNNNNNNNNNNNNNNNO    ","    IAAAAAAAAAAAAAAAAAO    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","   D D               D D   "},
                        {"   D D               D D   ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","    I                 O    ","    IGGGGGGGGGGGGGGGGGO    ","    I                 O    ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","   D D               D D   "},
                        {"   D D               D D   ","FFEEIEEEEEEED     DEEEOEE  ","    IAAAAAAAAAAAAAAAAAO    ","    LNNNNNNNNNNNNNNNNNO    ","    I                 O    ","    IGGGGGGGGGGGGGGGGGO    ","    I                 O    ","    LNNNNNNNNNNNNNNNNNO    ","    IAAAAAAAAAAAAAAAAAO    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","   D D               D D   "},
                        {"   D D               D D   ","    I       DFFFFFD   O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","    LNNNNNNNNNNNNNNNNNO    ","    IGGGGGGGGGGGGGGGGGOCCCC","    LNNNNNNNNNNNNNNNNNO    ","    I                 O    ","    IAAAAAAAAAAAAAAAAAO    ","    I                 O    ","   D D               D D   "},
                        {"             JJJJJ         ","   D D      DJJJJJD  D D   ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO   C","    IHHHHHHHHHHHHHHHHHO    ","    IHHHHHHHHHHHHHHHHHO    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","   D D               D D   ","                           "},
                        {"             JJ~JJ         ","            DJJJJJD        ","   D D               D D   ","    I                 O    ","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    I                 O   C","FFEEIEEEEEEEEEEEEEEEEEOEE  ","    I                 O    ","   D D               D D   ","                           ","                           "},
                        {"             JJJJJCCCCCCCCC","            DJJJJJD       C","                          C","   D D               D D  C","   D D               D D  C","   D D               D D  C","   D D               D D   ","   D D               D D   ","                           ","                           ","                           "}
                    }))
                //spotless:on
                .addElement('A', chainAllGlasses())
                .addElement('C', Casings.SteelPipeCasing.asElement())
                .addElement('D', Casings.ChemicallyInertMachineCasing.asElement())
                .addElement('E', ofFrame(Materials.Neodymium))
                .addElement('F', ofFrame(Materials.RedSteel))
                .addElement('G', Casings.ChamberGrate.asElement())
                .addElement('H', Casings.ElectrolyzerCasing.asElement())
                .addElement(
                    'J',
                    buildHatchAdder(MTEEMMA.class).atLeast(Maintenance, Energy)
                        .casingIndex(Casings.ElectrolyzerCasing.textureId)
                        .hint(1)
                        .buildAndChain(onElementPass(MTEEMMA::onCasingAdded, Casings.ElectrolyzerCasing.asElement())))
                .addElement(
                    'I',
                    buildHatchAdder(MTEEMMA.class).atLeast(InputBus, InputHatch)
                        .casingIndex(Casings.ElectrolyzerCasing.textureId)
                        .hint(2)
                        .buildAndChain(onElementPass(MTEEMMA::onCasingAdded, Casings.ElectrolyzerCasing.asElement())))
                .addElement(
                    'O',
                    buildHatchAdder(MTEEMMA.class).atLeast(OutputBus, OutputHatch)
                        .casingIndex(Casings.ElectrolyzerCasing.textureId)
                        .hint(3)
                        .buildAndChain(onElementPass(MTEEMMA::onCasingAdded, Casings.ElectrolyzerCasing.asElement())))
                .addElement(
                    'L',
                    buildHatchAdder(MTEEMMA.class).atLeast(ElectrodeHatch)
                        .casingIndex(Casings.ElectrolyzerCasing.textureId)
                        .hint(4)
                        .buildAndChain(onElementPass(MTEEMMA::onCasingAdded, Casings.ElectrolyzerCasing.asElement())))
                .addElement('B', ofBlock(GregTechAPI.sBlockMetal10, 2)) // Prismatic Naquadah
                .addElement('K', ofBlock(WerkstoffLoader.BWBlocks, GGMaterial.preciousMetalAlloy.getmID())) // Precious
                                                                                                            // Metals
                                                                                                            // Alloy
                .addElement('M', ofBlock(GregTechAPI.sBlockMetal9, 3)) // SpaceTime
                .addElement('N', ofBlock(GregTechAPI.sBlockMetal9, 6)) // White Dwarf Matter
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        return Textures.BlockIcons.createTextureWithCasing(
            this,
            side,
            aFacing,
            aActive,
            TEXTURE_CONTROLLER,
            TEXTURE_CONTROLLER_GLOW,
            TEXTURE_CONTROLLER_ACTIVE,
            TEXTURE_CONTROLLER_ACTIVE_GLOW);
    }

    @Override
    public ITexture getCasingTexture() {
        return Casings.ElectrolyzerCasing.getCasingTexture();
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Electrolyzer, EMMA")
            .beginStructureBlock(5, 5, 5, false)
            .addController("Front center, 3rd layer")
            .addCasing("6-43", "Electrolyzer Casing", false) // Fix amount
            .addCasing("12", "Potin Frame Box", false)
            .addCasing("4", "Tin Item Pipe Casing", false)
            .addCasing("4", "Brass Item Pipe Casing", false)
            .addEnergyHatch("1+", "Any electrolyzer casing", 1)
            .addMaintenanceHatch("1", "Any electrolyzer casing", 1)
            .addMufflerHatch("1", "Any electrolyzer casing", 1)
            .addInputAny("1+", "Any electrolyzer casing", 1)
            .addOutputAny("1+", "Any electrolyzer casing", 1)
            .addStructureAuthors(
                EnumChatFormatting.GREEN + "error1number404"
                    + EnumChatFormatting.GRAY
                    + " & "
                    + EnumChatFormatting.BLUE
                    + "revurii")
            .toolTipFinisher();
        return tt;
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic().setSpeedBonus(1F / SPEED)
            .setEuModifier(EU_EFFICIENCY)
            .setMaxParallelSupplier(this::getTrueParallel);
    }

    @Override
    public int getMaxParallelRecipes() {
        return PARALLEL_PER_TIER * GTUtility.getTier(this.getMaxInputVoltage());
    }

    private int casingAmount;
    private int structureTier;

    private void onCasingAdded() {
        casingAmount++;
    }

    private static String getStructurePiece(ItemStack stackSize) {
        if (stackSize == null || stackSize.stackSize <= 1) return STRUCTURE_TIER_1;
        if (stackSize.stackSize == 2) return STRUCTURE_TIER_2;
        if (stackSize.stackSize == 3) return STRUCTURE_TIER_3;
        return STRUCTURE_TIER_4;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(getStructurePiece(stackSize), stackSize, hintsOnly, OFFSET_X, OFFSET_Y, OFFSET_Z);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            getStructurePiece(stackSize),
            stackSize,
            OFFSET_X,
            OFFSET_Y,
            OFFSET_Z,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        structureTier = 0;
        String[] pieces = { STRUCTURE_TIER_4, STRUCTURE_TIER_3, STRUCTURE_TIER_2, STRUCTURE_TIER_1 };
        int[] tiers = { 4, 3, 2, 1 };
        for (int i = 0; i < pieces.length; i++) {
            clearHatches();
            casingAmount = 0;
            errors.clear();
            if (!checkPiece(pieces[i], OFFSET_X, OFFSET_Y, OFFSET_Z, errors)) continue;
            structureTier = tiers[i];
            checkCasingMin(errors, casingAmount, 6);
            checkHasEnergyHatch(errors);
            checkHasMaintenanceHatch(errors);
            checkHasAnyInput(errors);
            checkHasAnyOutput(errors);
            return;
        }
        errors.add(UNKNOWN_TIER);
    }

    // maybe move implementation not per each multi but kinda like regular input buses somewhere TODO
    private final List<MTEElectrodeHatch> electrodeHatch = new ArrayList<>();
    private final List<MTEElectrodeDetectorHatch> electrodeDetectorHatch = new ArrayList<>();

    @Override
    public void clearHatches() {
        super.clearHatches();
        electrodeHatch.clear();
        electrodeDetectorHatch.clear();
    }

    private boolean addElectrodeHatchToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (electrodeHatch != null) return false;
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEElectrodeHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            hatch.updateCraftingIcon(this.getMachineCraftingIcon());
            electrodeHatch.add(hatch);
            return true;
        }
        return false;
    }

    private boolean addElectrodeDetectorHatchToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEElectrodeDetectorHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            hatch.updateCraftingIcon(this.getMachineCraftingIcon());
            electrodeDetectorHatch.add(hatch);
            return true;
        }
        return false;
    }

    enum EMMAElectrodeHatches implements IHatchElement<MTEEMMA> {

        ElectrodeHatch(MTEEMMA::addElectrodeHatchToMachineList, MTEElectrodeHatch.class) {

            @Override
            public long count(MTEEMMA t) {
                if (t.electrodeHatch == null) return 0;
                return 1;
            }
        },
        ElectrodeDetectorHatch(MTEEMMA::addElectrodeDetectorHatchToMachineList, MTEElectrodeDetectorHatch.class) {

            @Override
            public long count(MTEEMMA t) {
                return t.electrodeDetectorHatch.size();
            }
        },;

        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<MTEEMMA> adder;

        @SafeVarargs
        EMMAElectrodeHatches(IGTHatchAdder<MTEEMMA> adder, Class<? extends IMetaTileEntity>... mteClasses) {
            this.mteClasses = Collections.unmodifiableList(Arrays.asList(mteClasses));
            this.adder = adder;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super MTEEMMA> adder() {
            return adder;
        }
    }

    @Override
    public int getPollutionPerSecond(final ItemStack aStack) {
        return 0;
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.electrolyzerNonCellRecipes;
    }

    @Override
    public boolean supportsInputSeparation() {
        return true;
    }

    @Override
    public boolean supportsSingleRecipeLocking() {
        return true;
    }

    @Override
    public boolean supportsVoidProtection() {
        return true;
    }

    @Override
    public boolean supportsBatchMode() {
        return true;
    }
}
