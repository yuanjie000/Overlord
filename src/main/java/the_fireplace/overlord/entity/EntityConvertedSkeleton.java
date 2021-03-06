package the_fireplace.overlord.entity;

import com.google.common.collect.Lists;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntitySpectralArrow;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.internal.FMLNetworkHandler;
import the_fireplace.overlord.Overlord;
import the_fireplace.overlord.advancements.CriterionRegistry;
import the_fireplace.overlord.config.ConfigValues;
import the_fireplace.overlord.entity.ai.EntityAIArmyBow;
import the_fireplace.overlord.network.PacketDispatcher;
import the_fireplace.overlord.network.packets.RequestAugmentMessage;
import the_fireplace.overlord.registry.AugmentRegistry;
import the_fireplace.overlord.tools.Augment;
import the_fireplace.overlord.tools.ISkinsuitWearer;
import the_fireplace.overlord.tools.SkinType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.UUID;

/**
 * @author The_Fireplace
 */
public class EntityConvertedSkeleton extends EntityArmyMember implements ISkinsuitWearer {
	private static final DataParameter<String> SKINSUIT_NAME = EntityDataManager.createKey(EntityConvertedSkeleton.class, DataSerializers.STRING);
	private static final DataParameter<Byte> SKINSUIT_TYPE = EntityDataManager.createKey(EntityConvertedSkeleton.class, DataSerializers.BYTE);
	private EntityAIArmyBow aiArrowAttack = null;

	public final InventoryBasic inventory;
	public final InventoryBasic equipInventory;

	public EntityConvertedSkeleton instance;

	public EntityConvertedSkeleton(World world) {
		this(world, null);
	}

	public EntityConvertedSkeleton(World world, @Nullable UUID owner) {
		super(world, owner);
		instance = this;
		this.inventory = new InventoryBasic("Items", false, 9);
		this.equipInventory = new InventoryBasic("Equipment", false, 7) {
			@Override
			public boolean isItemValidForSlot(int index, ItemStack stack) {
				return (index >= 4 && index < 6) || (index == 6 && AugmentRegistry.getAugment(stack) != null) || !stack.isEmpty() && stack.getItem().isValidArmor(stack, EntityEquipmentSlot.values()[index], null);
			}

			@Override
			public void setInventorySlotContents(int index, ItemStack stack) {
				super.setInventorySlotContents(index, stack);
				if (world.isRemote && index == 6)
					PacketDispatcher.sendToServer(new RequestAugmentMessage(instance));
			}

			@Override
			@Nonnull
			public ItemStack removeStackFromSlot(int index) {
				ItemStack stack = super.removeStackFromSlot(index);
				if (world.isRemote && index == 6)
					PacketDispatcher.sendToServer(new RequestAugmentMessage(instance));
				return stack;
			}

			@Override
			@Nonnull
			public ItemStack decrStackSize(int index, int count) {
				ItemStack stack = super.decrStackSize(index, count);
				if (world.isRemote && index == 6)
					PacketDispatcher.sendToServer(new RequestAugmentMessage(instance));
				return stack;
			}
		};
		this.setCanPickUpLoot(true);
	}

	@Override
	public void addAttackTasks() {
		if (aiAttackOnCollide == null) {
			aiAttackOnCollide = new EntityAIAttackMelee(this, 1.2D, false) {
				@Override
				public void resetTask() {
					super.resetTask();
					EntityConvertedSkeleton.this.setSwingingArms(false);
				}

				@Override
				public void startExecuting() {
					super.startExecuting();
					raiseArmTicks = 0;
				}

				@Override
				public void updateTask() {
					if (!this.attacker.getHeldItemMainhand().isEmpty() && !this.attacker.getHeldItemOffhand().isEmpty() && this.attacker.getHeldItemMainhand().getItem() instanceof ItemBow && this.attacker.getHeldItemOffhand().getItem() instanceof ItemArrow) {
						((EntityArmyMember) this.attacker).initEntityAI();
						return;
					}
					if (shouldContinueExecuting()) {
						++raiseArmTicks;

						if (raiseArmTicks >= 5 && this.attackTick < 10) {
							EntityConvertedSkeleton.this.setSwingingArms(true);
						} else {
							EntityConvertedSkeleton.this.setSwingingArms(false);
						}
						super.updateTask();
					}
				}
			};
		}
		if (aiArrowAttack == null)
			aiArrowAttack = new EntityAIArmyBow(this, 1.0D, 20, 15.0F);
		if (!this.getHeldItemMainhand().isEmpty())
			if (this.getHeldItemMainhand().getItem() instanceof ItemBow) {
				this.tasks.addTask(5, aiArrowAttack);
				return;
			}
		if (this.dataManager.get(MOVEMENT_MODE) > 0)
			this.tasks.addTask(5, aiAttackOnCollide);
	}

	@Override
	protected void applyEntityAttributes() {
		super.applyEntityAttributes();
		this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.25D);
		this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(35.0D);
		this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(2.0D);
		this.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(0.5D);
		this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
	}

	@Override
	public boolean processInteract(EntityPlayer player, EnumHand hand) {
		EntityLivingBase owner = this.getOwner();
		if (owner != null) {
			if (owner.equals(player)) {
				if (!player.isSneaking()) {
					FMLNetworkHandler.openGui(player, Overlord.instance, hashCode(), world, (int) this.posX, (int) this.posY, (int) this.posZ);
					return true;
				} else {
					if (!world.isRemote) {
						ItemStack stack = player.getHeldItem(hand);
						if (!stack.isEmpty()) {
							if (stack.getItem() == Overlord.converted_spawner) {
								NBTTagCompound compound = new NBTTagCompound();
								this.writeEntityToNBT(compound);
								stack.setTagCompound(compound);
							}
						}
					}
				}
			}
		}
		return super.processInteract(player, hand);
	}

	@Override
	protected void entityInit() {
		super.entityInit();
		this.dataManager.register(SKINSUIT_TYPE, Byte.valueOf((byte) SkinType.NONE.ordinal()));
		this.dataManager.register(SKINSUIT_NAME, String.valueOf(""));
	}

	@Override
	public Augment getAugment() {
		if (equipInventory == null)
			return null;
		if (AugmentRegistry.getAugment(getAugmentStack()) == null && world.isRemote)
			return getClientAugment();
		return AugmentRegistry.getAugment(getAugmentStack());
	}

	@Override
	@Nonnull
	public EnumCreatureAttribute getCreatureAttribute() {
		return EnumCreatureAttribute.UNDEAD;
	}

	static ItemStack bucket = new ItemStack(Items.BUCKET);
	static ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
	boolean sally = false;
	boolean armed = false;
	boolean crusader = false;

	@Override
	public void onLivingUpdate() {
		if (!this.world.isRemote) {
			for (int i = 0; i < this.inventory.getSizeInventory(); i++) {
				if (!inventory.getStackInSlot(i).isEmpty() && this.getHealth() < this.getMaxHealth()) {
					if (inventory.getStackInSlot(i).getItem() == Items.MILK_BUCKET) {
						this.heal(1);
						if (inventory.getStackInSlot(i).getCount() > 1)
							inventory.getStackInSlot(i).shrink(1);
						else
							inventory.setInventorySlotContents(i, ItemStack.EMPTY);
						inventory.addItem(bucket);
					} else if (inventory.getStackInSlot(i).getItem() == Overlord.milk_bottle) {
						this.heal(1);
						if (inventory.getStackInSlot(i).getCount() > 1)
							inventory.getStackInSlot(i).shrink(1);
						else
							inventory.setInventorySlotContents(i, ItemStack.EMPTY);
						inventory.addItem(bottle);
					}
				}
			}

			if (this.world.isDaytime()) {
				float brightness = this.getBrightness();
				BlockPos blockpos = this.getRidingEntity() instanceof EntityBoat ? (new BlockPos(this.posX, (double) Math.round(this.posY), this.posZ)).up() : new BlockPos(this.posX, (double) Math.round(this.posY), this.posZ);

				if (!getSkinType().protectsFromSun())
					if (brightness > 0.5F && this.rand.nextFloat() * 30.0F < (brightness - 0.4F) * 2.0F && this.world.canSeeSky(blockpos)) {
						boolean burn = true;
						ItemStack headwear = this.getItemStackFromSlot(EntityEquipmentSlot.HEAD);

						if (!headwear.isEmpty()) {
							if (ConfigValues.HELMETDAMAGE)
								if (headwear.isItemStackDamageable()) {
									headwear.setItemDamage(headwear.getItemDamage() + this.rand.nextInt(2));

									if (headwear.getItemDamage() >= headwear.getMaxDamage()) {
										this.renderBrokenItemStack(headwear);
										this.setItemStackToSlot(EntityEquipmentSlot.HEAD, ItemStack.EMPTY);
									}
								}

							burn = false;
						}

						if (burn)
							this.setFire(6);
					}
			}

			this.world.getEntitiesWithinAABB(EntityItem.class, this.getEntityBoundingBox().grow(1.0D, 0.0D, 1.0D)).stream().filter(entityitem -> !entityitem.isDead && !entityitem.getItem().isEmpty() && !entityitem.cannotPickup()).forEach(entityitem -> {
				ItemStack stack2 = inventory.addItem(entityitem.getItem());
				if (!stack2.isEmpty()) {
					if (stack2.getCount() != entityitem.getItem().getCount())
						playSound(SoundEvents.ENTITY_ITEM_PICKUP, 0.2F, ((rand.nextFloat() - rand.nextFloat()) * 0.7F + 1.0F) * 2.0F);
					entityitem.getItem().setCount(stack2.getCount());
					if (stack2.getItem() == Items.MILK_BUCKET || stack2.getItem() == Overlord.milk_bottle) {
						for (int i = 0; i < inventory.getSizeInventory(); i++) {
							if (!inventory.getStackInSlot(i).isEmpty() && (inventory.getStackInSlot(i).getItem() == Items.BUCKET || inventory.getStackInSlot(i).getItem() == Items.GLASS_BOTTLE)) {
								entityDropItem(inventory.getStackInSlot(i), 0.1F);
								inventory.setInventorySlotContents(i, ItemStack.EMPTY);
							}
						}
					}
				} else {
					entityitem.setDead();
					playSound(SoundEvents.ENTITY_ITEM_PICKUP, 0.2F, ((rand.nextFloat() - rand.nextFloat()) * 0.7F + 1.0F) * 2.0F);
				}
			});
			//Bow stuffs
			if (!getHeldItemMainhand().isEmpty()) {
				if (getHeldItemMainhand().getItem() instanceof ItemBow)
					if ((!getHeldItemOffhand().isEmpty() && !(getHeldItemOffhand().getItem() instanceof ItemArrow)) || getHeldItemOffhand().isEmpty()) {
						boolean swapWeapon = true;
						for (int i = 0; i < inventory.getSizeInventory(); i++) {
							if (!inventory.getStackInSlot(i).isEmpty() && inventory.getStackInSlot(i).getItem() instanceof ItemArrow) {
								ItemStack offhand = ItemStack.EMPTY;
								if (!getHeldItemOffhand().isEmpty())
									offhand = getHeldItemOffhand().copy();
								ItemStack arrows = inventory.getStackInSlot(i).copy();
								setHeldItem(EnumHand.OFF_HAND, arrows);
								inventory.setInventorySlotContents(i, offhand);
								swapWeapon = false;
								break;
							}
						}
						if (swapWeapon)
							for (int i = 0; i < inventory.getSizeInventory(); i++) {
								if (!inventory.getStackInSlot(i).isEmpty() && inventory.getStackInSlot(i).getItem() instanceof ItemSword) {
									ItemStack clone = inventory.getStackInSlot(i).copy();
									inventory.setInventorySlotContents(i, getHeldItemMainhand());
									setHeldItem(EnumHand.MAIN_HAND, clone);
									break;
								}
							}
					}
			}
			//Equipment Achievements
			EntityLivingBase owner = getOwner();
			if (!getHeldItemMainhand().isEmpty()) {
				if (owner != null) {
					if (owner instanceof EntityPlayerMP)
						if (!armed) {
							CriterionRegistry.instance.SKELETON_STATUS_UPDATE.trigger((EntityPlayerMP) owner, this, Items.WOODEN_SWORD, 0);
							armed = true;
						}
				}
			} else if (armed)
				armed = false;

			if (getSkinType().equals(SkinType.PLAYER)) {
				if (owner != null) {
					if (owner instanceof EntityPlayerMP)
						if (!sally) {
							CriterionRegistry.instance.SKELETON_STATUS_UPDATE.trigger((EntityPlayerMP) owner, this, Overlord.skinsuit, 0);
							sally = true;
						}
				}
			} else if (sally)
				sally = false;

			if (!getItemStackFromSlot(EntityEquipmentSlot.HEAD).isEmpty() && !getItemStackFromSlot(EntityEquipmentSlot.CHEST).isEmpty() && !getItemStackFromSlot(EntityEquipmentSlot.LEGS).isEmpty() && !getItemStackFromSlot(EntityEquipmentSlot.FEET).isEmpty()) {
				if (getItemStackFromSlot(EntityEquipmentSlot.HEAD).getItem() == Items.CHAINMAIL_HELMET && getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItem() == Items.CHAINMAIL_CHESTPLATE && getItemStackFromSlot(EntityEquipmentSlot.LEGS).getItem() == Items.CHAINMAIL_LEGGINGS && getItemStackFromSlot(EntityEquipmentSlot.FEET).getItem() == Items.CHAINMAIL_BOOTS) {
					if (!getHeldItemOffhand().isEmpty())
						if (getHeldItemOffhand().getTagCompound() != null && getHeldItemOffhand().getItem() instanceof ItemShield)
							if (getHeldItemOffhand().getTagCompound().equals(Overlord.crusaderShield().getTagCompound()))
								if (owner != null) {
									if (owner instanceof EntityPlayerMP)
										if (!crusader) {
											CriterionRegistry.instance.SKELETON_STATUS_UPDATE.trigger((EntityPlayerMP) owner, this, Items.SHIELD, 0);
											crusader = true;
										}
								}
				} else if (crusader)
					crusader = false;
			}
		}

		this.setSize(0.6F, 1.99F);

		float brightness = this.getBrightness();

		if (brightness > 0.5F && !getSkinType().protectsFromSun())
			this.idleTime += 1;
		super.onLivingUpdate();
	}

	@Override
	public void onDeath(@Nonnull DamageSource cause) {
		super.onDeath(cause);

		Entity damageCause = cause.getTrueSource();
		if (damageCause instanceof EntityCreeper && ((EntityCreeper) damageCause).getPowered() && !((EntityCreeper) damageCause).isAIDisabled()) {
			((EntityCreeper) damageCause).incrementDroppedSkulls();
			this.entityDropItem(new ItemStack(Items.SKULL), 0.0F);
		}

		if (!this.world.isRemote) {
			for (int i = 0; i < inventory.getSizeInventory(); i++) {
				if (!inventory.getStackInSlot(i).isEmpty()) {
					EntityItem entityitem = new EntityItem(world, posX, posY, posZ, inventory.getStackInSlot(i));
					entityitem.setDefaultPickupDelay();
					world.spawnEntity(entityitem);
				}
			}
			for (int i = 0; i < equipInventory.getSizeInventory(); i++) {
				if (!equipInventory.getStackInSlot(i).isEmpty()) {
					EntityItem entityitem = new EntityItem(world, posX, posY, posZ, equipInventory.getStackInSlot(i));
					entityitem.setDefaultPickupDelay();
					world.spawnEntity(entityitem);
				}
			}
		}
	}

	@Override
	public void readEntityFromNBT(NBTTagCompound compound) {
		super.readEntityFromNBT(compound);

		if (compound.hasKey("SkinsuitName")) {
			String s = compound.getString("SkinsuitName");
			this.dataManager.set(SKINSUIT_NAME, s);
		}
		if (compound.hasKey("HasSkinsuit")) {
			boolean b = compound.getBoolean("HasSkinsuit");
			this.dataManager.set(SKINSUIT_TYPE, Byte.valueOf((byte) (b ? 1 : 0)));
		}
		if (compound.hasKey("SkinsuitType")) {
			byte b = compound.getByte("SkinsuitType");
			this.dataManager.set(SKINSUIT_TYPE, Byte.valueOf(b));
		}
		NBTTagList mainInv = (NBTTagList) compound.getTag("SkeletonInventory");
		if (mainInv != null) {
			for (int i = 0; i < mainInv.tagCount(); i++) {
				NBTTagCompound item = (NBTTagCompound) mainInv.get(i);
				int slot = item.getByte("SlotSkeletonInventory");
				if (slot >= 0 && slot < inventory.getSizeInventory()) {
					inventory.setInventorySlotContents(slot, new ItemStack(item));
				}
			}
		} else {
			Overlord.logWarn("List was null when reading Converted Skeleton's Inventory");
		}
		NBTTagList armorInv = (NBTTagList) compound.getTag("SkeletonEquipment");
		if (armorInv != null) {
			for (int i = 0; i < armorInv.tagCount(); i++) {
				NBTTagCompound item = (NBTTagCompound) armorInv.get(i);
				int slot = item.getByte("SlotSkeletonEquipment");
				if (slot >= 0 && slot < equipInventory.getSizeInventory()) {
					equipInventory.setInventorySlotContents(slot, new ItemStack(item));
				}
			}
		} else
			Overlord.logWarn("List was null when reading Converted Skeleton's Equipment");
	}

	@Override
	public void writeEntityToNBT(NBTTagCompound compound) {
		super.writeEntityToNBT(compound);
		compound.setByte("SkinsuitType", this.dataManager.get(SKINSUIT_TYPE));
		compound.setString("SkinsuitName", this.dataManager.get(SKINSUIT_NAME));

		NBTTagList mainInv = new NBTTagList();
		for (int i = 0; i < inventory.getSizeInventory(); i++) {
			ItemStack is = inventory.getStackInSlot(i);
			if (!is.isEmpty()) {
				NBTTagCompound item = new NBTTagCompound();

				item.setByte("SlotSkeletonInventory", (byte) i);
				is.writeToNBT(item);

				mainInv.appendTag(item);
			}
		}
		compound.setTag("SkeletonInventory", mainInv);

		NBTTagList armorInv = new NBTTagList();
		for (int i = 0; i < equipInventory.getSizeInventory(); i++) {
			ItemStack is = equipInventory.getStackInSlot(i);
			if (!is.isEmpty()) {
				NBTTagCompound item = new NBTTagCompound();

				item.setByte("SlotSkeletonEquipment", (byte) i);
				is.writeToNBT(item);

				armorInv.appendTag(item);
			}
		}
		compound.setTag("SkeletonEquipment", armorInv);
	}

	@Override
	public float getEyeHeight() {
		return 1.74F;
	}

	@Override
	public double getYOffset() {
		return -0.35D;
	}

	@Override
	public void attackEntityWithRangedAttack(EntityLivingBase target, float distanceFactor) {
		ItemStack arrowItem = this.getHeldItemOffhand();
		if (arrowItem.isEmpty() || !(arrowItem.getItem() instanceof ItemArrow))
			return;

		EntityArrow entityarrow = getArrow(distanceFactor);
		double d0 = target.posX - this.posX;
		double d1 = target.getEntityBoundingBox().minY + (double) (target.height / 3.0F) - entityarrow.posY;
		double d2 = target.posZ - this.posZ;
		double d3 = (double) MathHelper.sqrt(d0 * d0 + d2 * d2);
		entityarrow.setThrowableHeading(d0, d1 + d3 * 0.20000000298023224D, d2, 1.6F, (float) (14 - this.world.getDifficulty().getDifficultyId() * 4));

		if (EnchantmentHelper.getMaxEnchantmentLevel(Enchantments.INFINITY, this) <= 0) {
			if (arrowItem.getCount() > 1)
				arrowItem.shrink(1);
			else
				setHeldItem(EnumHand.OFF_HAND, ItemStack.EMPTY);
			entityarrow.pickupStatus = EntityArrow.PickupStatus.ALLOWED;
		}

		if (!getHeldItemMainhand().isEmpty())
			getHeldItemMainhand().damageItem(1, this);
		this.playSound(SoundEvents.ENTITY_SKELETON_SHOOT, 1.0F, 1.0F / (this.getRNG().nextFloat() * 0.4F + 0.8F));
		this.world.spawnEntity(entityarrow);
	}

	protected EntityArrow getArrow(float distanceFactor) {
		ItemStack arrowItem = this.getItemStackFromSlot(EntityEquipmentSlot.OFFHAND);

		if (arrowItem.getItem() == Items.SPECTRAL_ARROW) {
			EntitySpectralArrow entityspectralarrow = new EntitySpectralArrow(this.world, this);
			entityspectralarrow.setEnchantmentEffectsFromEntity(this, distanceFactor);
			return entityspectralarrow;
		} else {
			EntityTippedArrow entityarrow = new EntityTippedArrow(this.world, this);
			entityarrow.setEnchantmentEffectsFromEntity(this, distanceFactor);

			if (arrowItem.getItem() == Items.TIPPED_ARROW)
				entityarrow.setPotionEffect(arrowItem);

			return entityarrow;
		}
	}

	@Override
	@Nonnull
	public ItemStack getItemStackFromSlot(EntityEquipmentSlot slotIn) {
		return slotIn == EntityEquipmentSlot.MAINHAND ? equipInventory.getStackInSlot(4) : (slotIn == EntityEquipmentSlot.OFFHAND ? equipInventory.getStackInSlot(5) : (slotIn.getSlotType() == EntityEquipmentSlot.Type.ARMOR ? this.equipInventory.getStackInSlot(slotIn.getIndex()) : ItemStack.EMPTY));
	}

	@Override
	public void setItemStackToSlot(EntityEquipmentSlot slotIn, @Nonnull ItemStack stack) {
		if (slotIn == EntityEquipmentSlot.MAINHAND) {
			this.playEquipSound(stack);
			this.equipInventory.setInventorySlotContents(4, stack);
			initEntityAI();
		} else if (slotIn == EntityEquipmentSlot.OFFHAND) {
			this.playEquipSound(stack);
			this.equipInventory.setInventorySlotContents(5, stack);
		} else if (slotIn.getSlotType() == EntityEquipmentSlot.Type.ARMOR) {
			this.playEquipSound(stack);
			this.equipInventory.setInventorySlotContents(slotIn.getIndex(), stack);
		}
	}

	@Override
	@Nonnull
	public Iterable<ItemStack> getHeldEquipment() {
		return Lists.newArrayList(this.getHeldItemMainhand(), this.getHeldItemOffhand());
	}

	@Override
	@Nonnull
	public Iterable<ItemStack> getArmorInventoryList() {
		return Arrays.asList(equipInventory.getStackInSlot(0), equipInventory.getStackInSlot(1), equipInventory.getStackInSlot(2), equipInventory.getStackInSlot(3));
	}

	@Override
	@Nonnull
	public ItemStack getHeldItemMainhand() {
		if (equipInventory == null)
			return ItemStack.EMPTY;
		return equipInventory.getStackInSlot(4);
	}

	@Override
	@Nonnull
	public ItemStack getHeldItemOffhand() {
		if (equipInventory == null)
			return ItemStack.EMPTY;
		return equipInventory.getStackInSlot(5);
	}

	@Override
	public boolean isPlayer() {
		return true;
	}

	@Override
	@Nonnull
	public ItemStack getAugmentStack() {
		return equipInventory.getStackInSlot(6);
	}

	@Override
	@Nonnull
	public ItemStack getHeldItem(EnumHand hand) {
		if (hand == EnumHand.MAIN_HAND) {
			return getHeldItemMainhand();
		} else if (hand == EnumHand.OFF_HAND) {
			return getHeldItemOffhand();
		} else {
			throw new IllegalArgumentException("Invalid hand " + hand);
		}
	}

	@Override
	public void setHeldItem(EnumHand hand, @Nonnull ItemStack stack) {
		if (hand == EnumHand.MAIN_HAND) {
			this.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, stack);
		} else {
			if (hand != EnumHand.OFF_HAND)
				throw new IllegalArgumentException("Invalid hand " + hand);

			this.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, stack);
		}
	}

	@Override
	public float getBlockPathWeight(BlockPos pos) {
		if (!this.getSkinType().protectsFromSun())
			return 0.5F - this.world.getLightBrightness(pos);
		else
			return super.getBlockPathWeight(pos);
	}

	@Override
	public boolean willBeAttackedBy(@Nonnull EntityLiving mob) {
		return this.getSkinType().equals(SkinType.PLAYER) && (getItemStackFromSlot(EntityEquipmentSlot.HEAD).isEmpty() || getItemStackFromSlot(EntityEquipmentSlot.HEAD).getItem() != Items.SKULL || getItemStackFromSlot(EntityEquipmentSlot.HEAD).getMetadata() == 3);
	}

	@Override
	@Nonnull
	public SkinType getSkinType() {
		return SkinType.get(this.dataManager.get(SKINSUIT_TYPE));
	}

	@Override
	@Nonnull
	public String getSkinName() {
		return this.dataManager.get(SKINSUIT_NAME);
	}

	@SuppressWarnings("UnnecessaryBoxing")
	@Override
	public void setSkinsuit(@Nonnull ItemStack stack, @Nonnull SkinType type) {
		if (type.isNone()) {
			ItemStack dropStack = getSkinType().equals(SkinType.PLAYER) ? new ItemStack(Overlord.skinsuit) : new ItemStack(Overlord.skinsuit_mummy);
			if (ConfigValues.SKINSUITNAMETAGS && this.hasCustomName())
				if (this.getCustomNameTag().equals(getSkinName())) {
					dropStack.setStackDisplayName(getSkinName());
					this.setCustomNameTag("");
				}
			entityDropItem(dropStack, 0.1F);
		}

		this.dataManager.set(SKINSUIT_TYPE, Byte.valueOf((byte) type.ordinal()));
		if (stack.hasDisplayName() && !type.isNone()) {
			this.dataManager.set(SKINSUIT_NAME, String.valueOf(stack.getDisplayName()));
			if (ConfigValues.SKINSUITNAMETAGS && !this.hasCustomName())
				setCustomNameTag(stack.getDisplayName());
		} else
			this.dataManager.set(SKINSUIT_NAME, String.valueOf(""));
	}
}
