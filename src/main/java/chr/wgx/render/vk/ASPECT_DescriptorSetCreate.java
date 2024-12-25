package chr.wgx.render.vk;

import chr.wgx.render.RenderException;
import chr.wgx.render.data.Descriptor;
import chr.wgx.render.data.Texture;
import chr.wgx.render.data.UniformBuffer;
import chr.wgx.render.info.DescriptorSetCreateInfo;
import chr.wgx.render.vk.data.CombinedImageSampler;
import chr.wgx.render.vk.data.VulkanDescriptorSet;
import chr.wgx.render.vk.data.VulkanDescriptorSetLayout;
import chr.wgx.render.vk.data.VulkanUniformBuffer;
import org.jetbrains.annotations.Nullable;
import tech.icey.panama.annotation.enumtype;
import tech.icey.vk4j.Constants;
import tech.icey.vk4j.datatype.VkDescriptorBufferInfo;
import tech.icey.vk4j.datatype.VkDescriptorImageInfo;
import tech.icey.vk4j.datatype.VkDescriptorSetAllocateInfo;
import tech.icey.vk4j.datatype.VkWriteDescriptorSet;
import tech.icey.vk4j.enumtype.VkDescriptorType;
import tech.icey.vk4j.enumtype.VkImageLayout;
import tech.icey.vk4j.enumtype.VkResult;
import tech.icey.vk4j.handle.VkDescriptorPool;
import tech.icey.vk4j.handle.VkDescriptorSet;
import tech.icey.vk4j.handle.VkDescriptorSetLayout;

import java.lang.foreign.Arena;

public final class ASPECT_DescriptorSetCreate {
    ASPECT_DescriptorSetCreate(VulkanRenderEngine engine) {
        this.engine = engine;
    }

    public VulkanDescriptorSet createDescriptorSetImpl(DescriptorSetCreateInfo createInfo) throws RenderException {
        if (!(createInfo.layout instanceof VulkanDescriptorSetLayout vulkanLayout)) {
            throw new IllegalArgumentException("DescriptorSetCreateInfo::layout 不是 VulkanDescriptorSetLayout, 是否错误地混用了不同的渲染引擎?");
        }

        @Nullable VkDescriptorPool descriptorPool = engine.descriptorPools.get(vulkanLayout);
        if (descriptorPool == null) {
            throw new IllegalArgumentException("未找到对应的 VkDescriptorPool, 是否未正确创建?");
        }

        VulkanRenderEngineContext cx = engine.cx;
        try (Arena arena = Arena.ofConfined()) {
            VkDescriptorSetLayout.Buffer pSetLayouts = VkDescriptorSetLayout.Buffer.allocate(arena);
            pSetLayouts.write(vulkanLayout.layout);

            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.allocate(arena);
            allocInfo.descriptorPool(descriptorPool);
            allocInfo.descriptorSetCount(1);
            allocInfo.pSetLayouts(pSetLayouts);

            VkDescriptorSet.Buffer pDescriptorSet = VkDescriptorSet.Buffer.allocate(arena);
            @enumtype(VkResult.class) int result = cx.dCmd.vkAllocateDescriptorSets(cx.device, allocInfo, pDescriptorSet);
            if (result != VkResult.VK_SUCCESS) {
                throw new RenderException("无法分配描述符集, 错误代码: " + VkResult.explain(result));
            }

            VkDescriptorSet descriptorSet = pDescriptorSet.read();
            VkWriteDescriptorSet[] descriptorSetWrite = VkWriteDescriptorSet.allocate(arena, createInfo.descriptors.size());
            for (int i = 0; i < createInfo.descriptors.size(); i++) {
                Descriptor descriptor = createInfo.descriptors.get(i);
                VkWriteDescriptorSet write = descriptorSetWrite[i];

                write.dstSet(descriptorSet);
                write.dstBinding(i);
                write.dstArrayElement(0);
                write.descriptorCount(1);

                switch (descriptor) {
                    case Texture texture -> {
                        write.descriptorType(VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);

                        CombinedImageSampler combinedImageSampler = (CombinedImageSampler) texture;
                        VkDescriptorImageInfo imageInfo = VkDescriptorImageInfo.allocate(arena);
                        imageInfo.imageLayout(VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                        imageInfo.imageView(combinedImageSampler.image.value.imageView);
                        imageInfo.sampler(combinedImageSampler.sampler.sampler);

                        write.pImageInfo(imageInfo);
                    }
                    case UniformBuffer uniformBuffer -> {
                        write.descriptorType(VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);

                        VulkanUniformBuffer vulkanUniformBuffer = (VulkanUniformBuffer) uniformBuffer;
                        VkDescriptorBufferInfo bufferInfo = VkDescriptorBufferInfo.allocate(arena);
                        // TODO
                        bufferInfo.buffer(vulkanUniformBuffer.underlyingBuffer.getFirst().buffer);
                        bufferInfo.range(Constants.VK_WHOLE_SIZE);

                        write.pBufferInfo(bufferInfo);
                    }
                }

                cx.dCmd.vkUpdateDescriptorSets(cx.device, 1, write, 0, null);
            }

            VulkanDescriptorSet ret = new VulkanDescriptorSet(createInfo, descriptorSet);
            engine.descriptorSets.add(ret);
            return ret;
        }
    }

    private final VulkanRenderEngine engine;
}
