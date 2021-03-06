package the_fireplace.overlord.items;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.internal.FMLNetworkHandler;
import the_fireplace.overlord.Overlord;

import javax.annotation.Nonnull;

/**
 * @author The_Fireplace
 */
public class ItemSquadEditor extends Item {
	@SuppressWarnings("unchecked")
	@Override
	@Nonnull
	public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, @Nonnull EnumHand hand) {
		FMLNetworkHandler.openGui(playerIn, Overlord.instance, -2, worldIn, (int) playerIn.posX, (int) playerIn.posY, (int) playerIn.posZ);
		return new ActionResult(EnumActionResult.SUCCESS, playerIn.getHeldItem(hand));
	}
}
