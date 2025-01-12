package chr.wgx.render.vk;

import chr.wgx.config.Config;
import chr.wgx.config.VulkanConfig;
import chr.wgx.render.RenderException;
import org.jetbrains.annotations.Nullable;
import tech.icey.panama.annotation.enumtype;
import tech.icey.panama.annotation.pointer;
import tech.icey.vk4j.Constants;
import tech.icey.vk4j.bitmask.VkBufferUsageFlags;
import tech.icey.vk4j.bitmask.VkImageAspectFlags;
import tech.icey.vk4j.bitmask.VkImageUsageFlags;
import tech.icey.vk4j.bitmask.VkSampleCountFlags;
import tech.icey.vk4j.datatype.*;
import tech.icey.vk4j.enumtype.*;
import tech.icey.vk4j.handle.VkBuffer;
import tech.icey.vk4j.handle.VkImage;
import tech.icey.vk4j.handle.VkImageView;
import tech.icey.vk4j.handle.VkSampler;
import tech.icey.vma.bitmask.VmaAllocationCreateFlags;
import tech.icey.vma.datatype.VmaAllocationCreateInfo;
import tech.icey.vma.datatype.VmaAllocationInfo;
import tech.icey.vma.enumtype.VmaMemoryUsage;
import tech.icey.vma.handle.VmaAllocation;
import tech.icey.xjbutil.container.Pair;

import java.lang.foreign.Arena;

public final class Resource {
    public static final class Image implements IVkDisposable {
        public final VkImage image;
        public final VkImageView imageView;
        public final VmaAllocation allocation;

        private Image(
                VkImage image,
                VkImageView imageView,
                VmaAllocation allocation
        ) {
            this.image = image;
            this.imageView = imageView;
            this.allocation = allocation;
        }

        public static Image create(
                VulkanRenderEngineContext cx,
                int width,
                int height,
                int mipLevels,
                @enumtype(VkSampleCountFlags.class) int sampleCountFlags,
                @enumtype(VkFormat.class) int format,
                @enumtype(VkImageTiling.class) int tiling,
                @enumtype(VkImageUsageFlags.class) int usage,
                @enumtype(VkImageAspectFlags.class) int aspect
        ) throws RenderException {
            Pair<VkImage, VmaAllocation> pair = createImage(
                    cx,
                    width,
                    height,
                    mipLevels,
                    sampleCountFlags,
                    format,
                    tiling,
                    usage
            );
            VkImage image = pair.first();
            VmaAllocation allocation = pair.second();
            VkImageView imageView = createImageView(cx, image, format, aspect, mipLevels);

            return new Image(image, imageView, allocation);
        }

        @Override
        public void dispose(VulkanRenderEngineContext cx) {
            cx.dCmd.vkDestroyImageView(cx.device, imageView, null);
            cx.vma.vmaDestroyImage(cx.vmaAllocator, image, allocation);
        }
    }

    public static final class Sampler implements IVkDisposable {
        public final VkSampler sampler;

        private Sampler(VkSampler sampler) {
            this.sampler = sampler;
        }

        public static Sampler create(VulkanRenderEngineContext cx, int mipLevels) throws RenderException {
            VulkanConfig config = Config.config().vulkanConfig;

            try (Arena arena = Arena.ofConfined()) {
                VkSamplerCreateInfo createInfo = VkSamplerCreateInfo.allocate(arena);
                createInfo.magFilter(VkFilter.VK_FILTER_LINEAR);
                createInfo.minFilter(VkFilter.VK_FILTER_LINEAR);
                createInfo.addressModeU(VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT);
                createInfo.addressModeV(VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT);
                createInfo.addressModeW(VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT);
                createInfo.anisotropyEnable(config.enableAnisotropy ? Constants.VK_TRUE : Constants.VK_FALSE);
                createInfo.maxAnisotropy(config.anisotropyLevel);
                createInfo.borderColor(VkBorderColor.VK_BORDER_COLOR_INT_OPAQUE_BLACK);
                createInfo.unnormalizedCoordinates(Constants.VK_FALSE);
                createInfo.compareEnable(Constants.VK_FALSE);
                createInfo.compareOp(VkCompareOp.VK_COMPARE_OP_ALWAYS);
                createInfo.mipmapMode(VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_LINEAR);
                createInfo.mipLodBias(0);
                createInfo.minLod(0);
                createInfo.maxLod(mipLevels);

                VkSampler.Buffer pSampler = VkSampler.Buffer.allocate(arena);
                @enumtype(VkResult.class) int result = cx.dCmd.vkCreateSampler(cx.device, createInfo, null, pSampler);
                if (result != VkResult.VK_SUCCESS) {
                    throw new RenderException("无法创建 Vulkan 采样器, 错误代码: " + result);
                }

                return new Sampler(pSampler.read());
            }
        }

        @Override
        public void dispose(VulkanRenderEngineContext cx) {
            cx.dCmd.vkDestroySampler(cx.device, sampler, null);
        }
    }

    public static final class SwapchainImage implements IVkDisposable {
        public final VkImage image;
        public final VkImageView imageView;

        private SwapchainImage(VkImage image, VkImageView imageView) {
            this.image = image;
            this.imageView = imageView;
        }

        @Override
        public void dispose(VulkanRenderEngineContext cx) {
            cx.dCmd.vkDestroyImageView(cx.device, imageView, null);
        }

        public static SwapchainImage create(
                VulkanRenderEngineContext cx,
                VkImage image,
                @enumtype(VkFormat.class) int format
        ) throws RenderException {
            VkImageView imageView = createImageView(cx, image, format, VkImageAspectFlags.VK_IMAGE_ASPECT_COLOR_BIT, 1);
            return new SwapchainImage(image, imageView);
        }
    }

    public static final class Buffer implements IVkDisposable {
        public final VkBuffer buffer;
        public final VmaAllocation allocation;

        private Buffer(VkBuffer buffer, VmaAllocation allocation) {
            this.buffer = buffer;
            this.allocation = allocation;
        }

        @Override
        public void dispose(VulkanRenderEngineContext cx) {
            cx.vma.vmaDestroyBuffer(cx.vmaAllocator, buffer, allocation);
        }

        public static Buffer create(
                VulkanRenderEngineContext cx,
                long size,
                @enumtype(VkBufferUsageFlags.class) int usage,
                @enumtype(VmaAllocationCreateFlags.class) int allocationFlags,
                @Nullable @pointer VmaAllocationInfo allocationInfo
        ) throws RenderException {
            try (Arena arena = Arena.ofConfined()) {
                VkBufferCreateInfo createInfo = VkBufferCreateInfo.allocate(arena);
                createInfo.size(size);
                createInfo.usage(usage);
                createInfo.sharingMode(VkSharingMode.VK_SHARING_MODE_EXCLUSIVE);

                VmaAllocationCreateInfo allocationCreateInfo = VmaAllocationCreateInfo.allocate(arena);
                allocationCreateInfo.usage(VmaMemoryUsage.VMA_MEMORY_USAGE_AUTO);
                allocationCreateInfo.flags(allocationFlags);

                VkBuffer.Buffer pBuffer = VkBuffer.Buffer.allocate(arena);
                VmaAllocation.Buffer pAllocation = VmaAllocation.Buffer.allocate(arena);
                @enumtype(VkResult.class) int result = cx.vma.vmaCreateBuffer(
                        cx.vmaAllocator,
                        createInfo,
                        allocationCreateInfo,
                        pBuffer,
                        pAllocation,
                        allocationInfo
                );
                if (result != VkResult.VK_SUCCESS) {
                    throw new RenderException("无法分配 Vulkan 缓冲区, 错误代码: " + result);
                }

                return new Buffer(pBuffer.read(), pAllocation.read());
            }
        }
    }

    private static Pair<VkImage, VmaAllocation> createImage(
            VulkanRenderEngineContext cx,
            int width,
            int height,
            int mipLevels,
            @enumtype(VkSampleCountFlags.class) int sampleCountFlags,
            @enumtype(VkFormat.class) int format,
            @enumtype(VkImageTiling.class) int tiling,
            @enumtype(VkImageUsageFlags.class) int usage
    ) throws RenderException {
        try (Arena arena = Arena.ofConfined()) {
            VkImageCreateInfo createInfo = VkImageCreateInfo.allocate(arena);
            createInfo.imageType(VkImageType.VK_IMAGE_TYPE_2D);
            createInfo.extent().width(width);
            createInfo.extent().height(height);
            createInfo.extent().depth(1);
            createInfo.mipLevels(mipLevels);
            createInfo.arrayLayers(1);
            createInfo.format(format);
            createInfo.tiling(tiling);
            createInfo.usage(usage);
            createInfo.samples(sampleCountFlags);
            createInfo.sharingMode(VkSharingMode.VK_SHARING_MODE_EXCLUSIVE);

            VmaAllocationCreateInfo allocationCreateInfo = VmaAllocationCreateInfo.allocate(arena);
            allocationCreateInfo.usage(VmaMemoryUsage.VMA_MEMORY_USAGE_GPU_ONLY);

            VkImage.Buffer pImage = VkImage.Buffer.allocate(arena);
            VmaAllocation.Buffer pAllocation = VmaAllocation.Buffer.allocate(arena);
            @enumtype(VkResult.class) int result = cx.vma.vmaCreateImage(
                    cx.vmaAllocator,
                    createInfo,
                    allocationCreateInfo,
                    pImage,
                    pAllocation,
                    null
            );
            if (result != VkResult.VK_SUCCESS) {
                throw new RenderException("无法创建 Vulkan 图像, 错误代码: " + VkResult.explain(result));
            }

            return new Pair<>(pImage.read(), pAllocation.read());
        }
    }

    private static VkImageView createImageView(
            VulkanRenderEngineContext cx,
            VkImage image,
            @enumtype(VkFormat.class) int format,
            @enumtype(VkImageAspectFlags.class) int aspect,
            int mipLevels
    ) throws RenderException {
        try (Arena arena = Arena.ofConfined()) {
            VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.allocate(arena);
            createInfo.image(image);
            createInfo.viewType(VkImageViewType.VK_IMAGE_VIEW_TYPE_2D);
            createInfo.format(format);

            VkImageSubresourceRange subresourceRange = createInfo.subresourceRange();
            subresourceRange.aspectMask(aspect);
            subresourceRange.baseMipLevel(0);
            subresourceRange.levelCount(mipLevels);
            subresourceRange.baseArrayLayer(0);
            subresourceRange.layerCount(1);

            VkImageView.Buffer pImageView = VkImageView.Buffer.allocate(arena);
            @enumtype(VkResult.class) int result = cx.dCmd.vkCreateImageView(cx.device, createInfo, null, pImageView);
            if (result != VkResult.VK_SUCCESS) {
                throw new RenderException("无法创建 Vulkan 图像视图, 错误代码: " + result);
            }
            return pImageView.read();
        }
    }
}
