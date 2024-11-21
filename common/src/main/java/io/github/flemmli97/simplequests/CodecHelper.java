package io.github.flemmli97.simplequests;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class CodecHelper {

    // The default BlockPos Codec writes to an array, this writes to a map of x, y, z
    public static Codec<BlockPos> BLOCK_POS_CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(Codec.INT.fieldOf("x").forGetter(Vec3i::getX),
                    Codec.INT.fieldOf("y").forGetter(Vec3i::getY),
                    Codec.INT.fieldOf("z").forGetter(Vec3i::getZ)
            ).apply(instance, BlockPos::new));

    /**
     * Custom ItemStack Codec that tries to minimize data saved
     */

//    public static final Codec<ItemStack> ITEM_STACK_CODEC = tryCodec(ItemStack.ITEM_NON_AIR_CODEC
//                    .flatXmap(h -> DataResult.success(new ItemStack(h)),
//                            s -> s.getComponentsPatch().isEmpty() && s.getCount() == 1 ? DataResult.success(s.getItemHolder()) : DataResult.error(() -> "Not default itemstack")),
//            RecordCodecBuilder.create(inst -> inst.group(
//                    ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
//                    ExtraCodecs.POSITIVE_INT.optionalFieldOf("count").forGetter(stack -> stack.getCount() == 1 ? Optional.empty() : Optional.of(stack.getCount())),
//                    DataComponentPatch.CODEC.optionalFieldOf("components").forGetter((stack) -> stack.getComponentsPatch().isEmpty() ? Optional.empty() : Optional.of(stack.getComponentsPatch()))
//            ).apply(inst, (s, count, comp) -> new ItemStack(s, count.orElse(1), comp.orElse(DataComponentPatch.EMPTY)))));


    public static final Codec<ItemStack> ITEM_STACK_CODEC = tryCodec(ItemStack.CODEC
                    .flatXmap(h -> DataResult.success(new ItemStack(h.getItemHolder())),
                            s -> s.getComponentsPatch().isEmpty() && s.getCount() == 1 ? DataResult.success(s) : DataResult.error(() -> "Not default itemstack")),

            RecordCodecBuilder.create((instance) -> instance.group(Item.CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter((itemStack) -> itemStack.getComponentsPatch().isEmpty() ? DataComponentPatch.EMPTY : itemStack.getComponentsPatch()))
                    .apply(instance, (holder, dataComponentPatch) -> new ItemStack(holder, 1, dataComponentPatch))));

    public static <E> Codec<List<Either<E, Pair<E, String>>>> optionalDescriptiveList(Codec<E> codec, String error) {
        return nonEmptyList(Codec.either(codec, Codec.mapPair(codec.fieldOf("value"), Codec.STRING.fieldOf("description")).codec()), error);
    }

    public static <E> Codec<List<Pair<E, String>>> descriptiveList(Codec<E> codec, String error) {
        return nonEmptyList(Codec.mapPair(codec.fieldOf("value"), Codec.STRING.fieldOf("description")).codec(), error);
    }

    public static <E> Codec<List<E>> nonEmptyList(Codec<E> codec, String error) {
        Function<List<E>, DataResult<List<E>>> function = list -> {
            if (list.isEmpty())
                return DataResult.error(() -> error);
            return DataResult.success(list);
        };
        return codec.listOf().flatXmap(function, function);
    }

    public static <F> Codec<F> tryCodec(Codec<F> first, Codec<F> second) {
        return new TryCodec<>(first, second);
    }

    public static <E> Codec<List<E>> listOrInline(Codec<E> codec) {
        Codec<List<E>> listCodec = codec.listOf();
        return Codec.either(listCodec, codec).xmap(either -> either.map(list -> list, List::of),
                list -> list.size() == 1 ? Either.right(list.getFirst()) : Either.left(list));
    }

    private record TryCodec<F>(Codec<F> first, Codec<F> second) implements Codec<F> {

        @Override
        public <T> DataResult<Pair<F, T>> decode(final DynamicOps<T> ops, final T input) {
            final DataResult<Pair<F, T>> first = this.first.decode(ops, input);
            if (first.isSuccess()) {
                return first;
            }
            final DataResult<Pair<F, T>> second = this.second.decode(ops, input);
            if (second.isSuccess()) {
                return second;
            }
            return first.apply2((f, s) -> s, second);
        }

        @Override
        public <T> DataResult<T> encode(F input, final DynamicOps<T> ops, final T prefix) {
            DataResult<T> first = this.first.encode(input, ops, prefix);
            if (first.isSuccess())
                return first;
            DataResult<T> second = this.second.encode(input, ops, prefix);
            if (second.isSuccess())
                return second;
            return first.apply2((f, s) -> s, second);
        }
    }
}
