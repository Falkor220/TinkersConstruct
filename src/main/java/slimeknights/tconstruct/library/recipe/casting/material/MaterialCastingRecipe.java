package slimeknights.tconstruct.library.recipe.casting.material;

import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import slimeknights.mantle.recipe.IMultiRecipe;
import slimeknights.mantle.recipe.RecipeHelper;
import slimeknights.tconstruct.library.MaterialRegistry;
import slimeknights.tconstruct.library.materials.IMaterial;
import slimeknights.tconstruct.library.recipe.RecipeTypes;
import slimeknights.tconstruct.library.recipe.casting.AbstractCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.DisplayCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.ICastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.IDisplayableCastingRecipe;
import slimeknights.tconstruct.library.tinkering.IMaterialItem;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.recipe.ICastingInventory;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Casting recipe that takes an arbitrary fluid of a given amount and set the material on the output based on that fluid
 */
public abstract class MaterialCastingRecipe extends AbstractCastingRecipe implements IMultiRecipe<IDisplayableCastingRecipe> {
  private final int itemCost;
  private final IMaterialItem result;
  private List<IDisplayableCastingRecipe> multiRecipes;

  public MaterialCastingRecipe(IRecipeType<?> type, ResourceLocation id, String group, Ingredient cast, int itemCost, IMaterialItem result, boolean consumed, boolean switchSlots) {
    super(type, id, group, cast, consumed, switchSlots);
    this.itemCost = itemCost;
    this.result = result;
  }

  @Override
  public int getCoolingTime(ICastingInventory inv) {
    IMaterial material = MaterialRegistry.getMaterial(inv.getFluid());
    return ICastingRecipe.calcCoolingTime(material.getTemperature(), material.getFluidPerUnit() * this.itemCost);
  }

  @Override
  public boolean matches(ICastingInventory inv, World worldIn) {
    if (!this.cast.test(inv.getStack())) {
      return false;
    }
    IMaterial material = MaterialRegistry.getInstance().getMaterial(inv.getFluid());
    return material != IMaterial.UNKNOWN && result.canUseMaterial(material);
  }

  @Override
  public NonNullList<Ingredient> getIngredients() {
    return NonNullList.from(Ingredient.EMPTY, this.cast);
  }

  @Override
  public ItemStack getRecipeOutput() {
    return new ItemStack(result);
  }

  @Override
  public int getFluidAmount(ICastingInventory inv) {
    return MaterialRegistry.getMaterial(inv.getFluid()).getFluidPerUnit() * this.itemCost;
  }

  @Override
  public ItemStack getCraftingResult(ICastingInventory inv) {
    IMaterial material = MaterialRegistry.getInstance().getMaterial(inv.getFluid());
    return result.withMaterial(material);
  }

  @Override
  @SuppressWarnings("WeakerAccess")
  public List<IDisplayableCastingRecipe> getRecipes() {
    if (multiRecipes == null) {
      IRecipeType<?> type = getType();
      List<ItemStack> castItems = Arrays.asList(cast.getMatchingStacks());
      multiRecipes = MaterialRegistry
        .getMaterials().stream()
        .filter(mat -> mat.getFluid() != Fluids.EMPTY && result.canUseMaterial(mat))
        .map(mat -> new DisplayCastingRecipe(type, castItems, Collections.singletonList(new FluidStack(mat.getFluid(), itemCost * mat.getFluidPerUnit())),
                                             result.withMaterial(mat), ICastingRecipe.calcCoolingTime(mat.getTemperature(), itemCost * mat.getFluidPerUnit()), consumed))
        .collect(Collectors.toList());
    }
    return multiRecipes;
  }

  /** Basin implementation */
  public static class Basin extends MaterialCastingRecipe {

    public Basin(ResourceLocation id, String group, Ingredient cast, int fluidAmount, IMaterialItem result, boolean consumed, boolean switchSlots) {
      super(RecipeTypes.CASTING_BASIN, id, group, cast, fluidAmount, result, consumed, switchSlots);
      MaterialItemCostLookup.registerBasin(result, fluidAmount);
    }

    @Override
    public IRecipeSerializer<?> getSerializer() {
      return TinkerSmeltery.basinMaterialSerializer.get();
    }
  }

  /** Table implementation */
  public static class Table extends MaterialCastingRecipe {

    public Table(ResourceLocation id, String group, Ingredient cast, int fluidAmount, IMaterialItem result, boolean consumed, boolean switchSlots) {
      super(RecipeTypes.CASTING_TABLE, id, group, cast, fluidAmount, result, consumed, switchSlots);
      MaterialItemCostLookup.registerTable(result, fluidAmount);
    }

    @Override
    public IRecipeSerializer<?> getSerializer() {
      return TinkerSmeltery.tableMaterialSerializer.get();
    }
  }

  /**
   * Interface representing a material casting recipe constructor
   * @param <T>  Recipe class type
   */
  public interface IFactory<T extends MaterialCastingRecipe> {
    T create(ResourceLocation id, String group, @Nullable Ingredient cast, int fluidAmount, IMaterialItem result,
             boolean consumed, boolean switchSlots);
  }

  @RequiredArgsConstructor
  public static class Serializer<T extends MaterialCastingRecipe> extends AbstractCastingRecipe.Serializer<T> {
    private final IFactory<T> factory;

    @Override
    protected T create(ResourceLocation idIn, String groupIn, @Nullable Ingredient cast, boolean consumed, boolean switchSlots, JsonObject json) {
      int fluidAmount = JSONUtils.getInt(json, "item_cost");
      IMaterialItem result = RecipeHelper.deserializeItem(JSONUtils.getString(json, "result"), "result", IMaterialItem.class);
      return this.factory.create(idIn, groupIn, cast, fluidAmount, result, consumed, switchSlots);
    }

    @Override
    protected T create(ResourceLocation idIn, String groupIn, @Nullable Ingredient cast, boolean consumed, boolean switchSlots, PacketBuffer buffer) {
      int fluidAmount = buffer.readInt();
      IMaterialItem result = RecipeHelper.readItem(buffer, IMaterialItem.class);
      return this.factory.create(idIn, groupIn, cast, fluidAmount, result, consumed, switchSlots);
    }

    @Override
    protected void writeExtra(PacketBuffer buffer, MaterialCastingRecipe recipe) {
      buffer.writeInt(recipe.itemCost);
      RecipeHelper.writeItem(buffer, recipe.result);
    }
  }
}
