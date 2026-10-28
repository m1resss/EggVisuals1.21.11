package delta.module.misc;

import delta.config.DescriptionProcessor;
import delta.config.EnchantmentProcessor;
import delta.config.PotionProcessor;
import delta.render.AnimationUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class Collector_2 {

    public static class b {
        private final ItemStack itemStack;
        private final String name;
        private int count;
        private boolean active;
        private boolean flag;
        private int color;
        private EnchantmentProcessor enchantments;
        private DescriptionProcessor descriptions;
        private PotionProcessor potions;
        private final AnimationUtil animation = new AnimationUtil();

        public b(Item item, int count, String name) {
            this.itemStack = new ItemStack(item, count);
            this.count = count;
            this.name = name;
        }

        public static b a(Item item, int count, String name) {
            return new b(item, count, name);
        }

        public b a(boolean active) {
            this.active = active;
            return this;
        }

        public b b(boolean flag) {
            this.flag = flag;
            return this;
        }

        public b b(int count) {
            this.count = count;
            this.itemStack.setCount(count);
            return this;
        }

        public b a(int color) {
            this.color = color;
            return this;
        }

        public b a(EnchantmentProcessor enchantments) {
            this.enchantments = enchantments;
            return this;
        }

        public b a(DescriptionProcessor descriptions) {
            this.descriptions = descriptions;
            return this;
        }

        public b a(PotionProcessor potions) {
            this.potions = potions;
            return this;
        }

        public boolean a() {
            return this.flag;
        }

        public int b() {
            return this.itemStack.getMaxCount();
        }

        public ItemStack c() {
            return this.itemStack;
        }

        public PotionProcessor d() {
            return this.potions;
        }

        public DescriptionProcessor e() {
            return this.descriptions;
        }

        public EnchantmentProcessor f() {
            return this.enchantments;
        }

        public AnimationUtil h() {
            return this.animation;
        }

        public String j() {
            return this.name;
        }

        public boolean k() {
            return this.active;
        }

        public int m() {
            return this.count;
        }
    }
}
