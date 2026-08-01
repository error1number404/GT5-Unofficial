package gregtech.common.tileentities.machines.multi;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
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
import static net.minecraft.util.EnumChatFormatting.BOLD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.google.common.collect.ImmutableList;
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

    public static class Electrolyte {

        public final Materials material;
        public final float reactivity;
        public final int amountToBecomeReactive; // L needed to reach reactive state

        public Electrolyte(Materials material, float reactivity, int amountToBecomeReactive) {
            this.material = material;
            this.reactivity = reactivity;
            this.amountToBecomeReactive = amountToBecomeReactive;
        }

        public FluidStack getStack(int amount) {
            FluidStack stack = material.getFluid(amount);
            return stack != null ? stack : material.getMolten(amount);
        }

        public Fluid getFluid() {
            FluidStack stack = getStack(1);
            return stack == null ? null : stack.getFluid();
        }

    }

    private static final List<List<Electrolyte>> ELECTROLYTES = ImmutableList.of(
        // Tier 1
        ImmutableList.of(
            new Electrolyte(Materials.Grade1PurifiedWater, 0.6f, 175000),
            new Electrolyte(Materials.Grade2PurifiedWater, 0.4f, 100000)),
        // Tier 2
        ImmutableList.of(
            new Electrolyte(Materials.Grade3PurifiedWater, 0.7f, 350000),
            new Electrolyte(Materials.Grade4PurifiedWater, 0.3f, 200000)),
        // Tier 3
        ImmutableList.of(
            new Electrolyte(Materials.Grade5PurifiedWater, 0.8f, 550000),
            new Electrolyte(Materials.Grade6PurifiedWater, 0.2f, 300000)),
        // Tier 4
        ImmutableList.of(
            new Electrolyte(Materials.Grade7PurifiedWater, 0.9f, 750000),
            new Electrolyte(Materials.Grade8PurifiedWater, 0.1f, 400000),
            new Electrolyte(Materials.BioMediumSterilized, 1.0f, 750000),
            new Electrolyte(Materials.GrowthMediumSterilized, 0.01f, 500000)));

    private final Map<Fluid, Long> validFluidMap = new HashMap<>() {

        private static final long serialVersionUID = -8452610443191188130L;

        {
            for (int i = 0; i < ELECTROLYTES.size(); i++) {
                for (Electrolyte electrolyte : ELECTROLYTES.get(i)) {
                    put(electrolyte.getFluid(), 0L);
                }
            }
            put(base.mFluid, 0L);
        }
    };

    private Long getTakenInternalCapacity() {
        Long result = 0L;
        for (Map.Entry<Fluid, Long> entry : validFluidMap.entrySet()) {
            result += entry.getValue();
        }
        return result;
    }

    @Override
    public void onPreTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPreTick(aBaseMetaTileEntity, aTick);

        if (mMachine) {
            if ((aTick % TICKS_BETWEEN_HATCH_DRAIN) == 0) {
                drainFluidFromHatchesAndStoreInternally();
            }
        }
    }

    private long getInternalCapacity() {
        if (structureTier < 1 || structureTier > INTERNAL_CAPACITY.size()) return 0L;
        return INTERNAL_CAPACITY.get(structureTier - 1);
    }

    @Override
    public String[] getInfoData() {
        ArrayList<String> str = new ArrayList<>(Arrays.asList(super.getInfoData()));
        long taken = getTakenInternalCapacity();
        long max = getInternalCapacity();
        str.add(
            EnumChatFormatting.YELLOW + "Internal Capacity: "
                + EnumChatFormatting.RESET
                + formatNumber(taken)
                + " / "
                + formatNumber(max)
                + " L");
        validFluidMap.forEach((fluid, amount) -> {
            if (amount > 0) {
                str.add(
                    EnumChatFormatting.BLUE + fluid.getLocalizedName()
                        + EnumChatFormatting.RESET
                        + ": "
                        + EnumChatFormatting.AQUA
                        + formatNumber(amount)
                        + " L");
            }
        });
        return str.toArray(new String[0]);
    }

    private void drainFluidFromHatchesAndStoreInternally() {
        Long internalCapacity = getTakenInternalCapacity();
        if (internalCapacity >= getInternalCapacity()) {
            return;
        }
        Long capacityLeft = INTERNAL_CAPACITY.get(structureTier) - internalCapacity;
        List<FluidStack> fluidStacks = getStoredFluids();
        for (FluidStack fluidStack : fluidStacks) {
            if (validFluidMap.containsKey(fluidStack.getFluid())) {
                Long toMerge = Math.min(capacityLeft, (long) fluidStack.amount);
                validFluidMap.merge(fluidStack.getFluid(), toMerge, Long::sum);
                fluidStack.amount -= toMerge.intValue();
                capacityLeft -= toMerge;
            }
            if (capacityLeft == 0) break;
        }
        updateSlots();
    }

    private static final Materials base = Materials.StableBaryonicMatter;

    private static final float SPEED_PER_100KL_OF_ELECTROLYTE = 0.125f;
    private static final ImmutableList<Long> INTERNAL_CAPACITY = ImmutableList.of(200000L, 400000L, 600000L, 800000L);
    private static final int ELECTRODE_DURA_PER_PARALLEL = 40;
    private static final float SPEED = 4f;
    private static final float EU_EFFICIENCY = 0.7f;
    private static final float ELECTRODE_EU_PENALTY = 4.0f;
    private static final float ELECTRODE_SPEED_PENALTY = 0.25f;
    private static final float ELECTRODE_DURABILITY_BOOST = 0.5f;
    private static final float ELECTRODE_EU_BOOST = 4.0f;
    private static final float ELECTRODE_SPEED_BOOST = 4.0f;
    private static final float ELECTRODE_DURABILITY_PENALTY = 2.0f;
    private static final float CONSUME_UP_TO = 0.0025f;
    private static final int TICKS_BETWEEN_HATCH_DRAIN = 5;

    private static final List<List<Float>> IMBALANCE_PENALTIES = ImmutableList.of(
        ImmutableList.of(0.05f, 0.25f),
        ImmutableList.of(0.1f, 0.50f),
        ImmutableList.of(0.2f, 0.75f),
        ImmutableList.of(0.3f, 0.99f));

    private static IStructureDefinition<MTEEMMA> STRUCTURE_DEFINITION = null;

    private static final int OFFSET_X = 15;
    private static final int OFFSET_Y = 9;
    private static final int OFFSET_Z = 0;

    private static final String STRUCTURE_TIER_1 = "t1";
    private static final String STRUCTURE_TIER_2 = "t2";
    private static final String STRUCTURE_TIER_3 = "t3";
    private static final String STRUCTURE_TIER_4 = "t4";

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
            .addInfo("Overclocks limited to " + EnumChatFormatting.WHITE + "Hatch Tier + 1" + EnumChatFormatting.GRAY)
            .addSupportAny()
            .addUnlimitedTierSkips()
            .addSeparator()
            .addInfo(
                "Gains " + EnumChatFormatting.WHITE
                    + "2"
                    + EnumChatFormatting.GRAY
                    + " Electrode hatches per Structure Tier")
            .addInfo(
                EnumChatFormatting.YELLOW + "1"
                    + EnumChatFormatting.GRAY
                    + " parallel for every "
                    + EnumChatFormatting.WHITE
                    + ELECTRODE_DURA_PER_PARALLEL
                    + EnumChatFormatting.GRAY
                    + " max durability of the sum of the electrodes")
            .addStaticSpeedInfo(SPEED)
            .addStaticEuEffInfo(EU_EFFICIENCY)
            .addInfo(
                "Every second, up to " + EnumChatFormatting.YELLOW
                    + String.format("%.2f", CONSUME_UP_TO * 100)
                    + "%"
                    + EnumChatFormatting.GRAY
                    + " of the electrolyte and base in internal storage is converted to deactivated electrolyte and returned")
            .addInfo(
                "Every second, " + EnumChatFormatting.YELLOW
                    + "1"
                    + EnumChatFormatting.GRAY
                    + " durability is consumed from a random electrode")
            .addSeparator()
            .addInfo("Internal capacity is determined by Structure Tier");
        for (int i = 0; i < INTERNAL_CAPACITY.size(); i++) {
            tt.addInfo(
                "Tier " + EnumChatFormatting.WHITE
                    + (i + 1)
                    + EnumChatFormatting.GRAY
                    + ": "
                    + EnumChatFormatting.AQUA
                    + INTERNAL_CAPACITY.get(i)
                    + EnumChatFormatting.GRAY
                    + " L");
        }
        tt.addInfo(
            "Any amount of electrolyte and base provided is consumed until " + EnumChatFormatting.AQUA
                + "internal capacity"
                + EnumChatFormatting.GRAY
                + " is reached")
            .addInfo(
                "Gains " + EnumChatFormatting.GREEN
                    + "x"
                    + SPEED_PER_100KL_OF_ELECTROLYTE
                    + EnumChatFormatting.GRAY
                    + " speed per 100 KL of electrolyte, up to "
                    + EnumChatFormatting.GREEN
                    + "x2")
            .addSeparator();
        tt.addInfo("Each Structure Tier unlocks new electrolytes");
        int m = 0;
        List<EnumChatFormatting> electrolyteColors = ImmutableList
            .of(EnumChatFormatting.GOLD, EnumChatFormatting.GREEN);
        for (int i = 0; i < ELECTROLYTES.size(); i++) {
            for (Electrolyte electrolyte : ELECTROLYTES.get(i)) {
                tt.addInfo(
                    "Tier " + EnumChatFormatting.WHITE
                        + (i + 1)
                        + EnumChatFormatting.GRAY
                        + ": "
                        + electrolyteColors.get(m % 2)
                        + electrolyte.material.getLocalizedName()
                        + EnumChatFormatting.GRAY
                        + " | "
                        + EnumChatFormatting.RED
                        + (int) (electrolyte.reactivity * 100)
                        + "%"
                        + EnumChatFormatting.GRAY
                        + " reactivity | "
                        + EnumChatFormatting.AQUA
                        + electrolyte.amountToBecomeReactive
                        + EnumChatFormatting.GRAY
                        + " KL");
                m++;
            }
        }
        tt.addInfo(
            "Some recipes " + EnumChatFormatting.RED
                + BOLD
                + "require"
                + EnumChatFormatting.RESET
                + EnumChatFormatting.GRAY
                + " certain "
                + EnumChatFormatting.GREEN
                + "reactivity %");
        tt.addSeparator()
            .addInfo(
                "Capacity that is not taken by electrolyte should be filled with " + EnumChatFormatting.LIGHT_PURPLE
                    + base.getLocalizedName())
            .addSeparator()
            .addInfo("Electrolyte imbalance affects speed");
        for (List<Float> penalty : IMBALANCE_PENALTIES) {
            tt.addInfo(
                EnumChatFormatting.YELLOW + ""
                    + (int) (penalty.get(0) * 100)
                    + "%"
                    + EnumChatFormatting.GRAY
                    + " imbalance: "
                    + EnumChatFormatting.RED
                    + String.format("%.2f", 1f - penalty.get(1))
                    + "x"
                    + EnumChatFormatting.GRAY
                    + " Speed");
        }
        tt.addSeparator()
            .addInfo("Electrodes insertion")
            .addInfo(
                "Lowering electrodes — up to " + EnumChatFormatting.RED
                    + "+5%"
                    + EnumChatFormatting.GRAY
                    + " reactivity (additive), up to "
                    + EnumChatFormatting.GREEN
                    + "4x"
                    + EnumChatFormatting.GRAY
                    + " Speed, "
                    + EnumChatFormatting.RED
                    + "4x"
                    + EnumChatFormatting.GRAY
                    + " EU cost, "
                    + EnumChatFormatting.YELLOW
                    + "2x"
                    + EnumChatFormatting.GRAY
                    + " durability cost")
            .addInfo(
                "Extracting electrodes — up to " + EnumChatFormatting.RED
                    + "−5%"
                    + EnumChatFormatting.GRAY
                    + " reactivity (additive), up to "
                    + EnumChatFormatting.GREEN
                    + "0.25x"
                    + EnumChatFormatting.GRAY
                    + " Speed, "
                    + EnumChatFormatting.RED
                    + "4x"
                    + EnumChatFormatting.GRAY
                    + " EU cost, "
                    + EnumChatFormatting.YELLOW
                    + "0.5x"
                    + EnumChatFormatting.GRAY
                    + " durability cost");
        tt.addInfo(
            "If an electrode is not present in any electrode hatch, the machine will " + EnumChatFormatting.RED
                + BOLD
                + "powerfail")
            .addSeparator()
            .addInfo(EnumChatFormatting.GREEN + "It's got what plants crave");
        tt.beginStructureBlock(5, 5, 5, false)
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
        return 10 * GTUtility.getTier(this.getMaxInputVoltage());
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
