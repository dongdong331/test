/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


#ifndef SF_GLES11RENDERENGINE_H_
#define SF_GLES11RENDERENGINE_H_

#include <stdint.h>
#include <sys/types.h>

#include <GLES/gl.h>
#include <Transform.h>

#include "RenderEngine.h"

// ---------------------------------------------------------------------------
namespace android {
// ---------------------------------------------------------------------------

class String8;
class Mesh;
class Texture;

namespace RE {
namespace impl {

class GLES11RenderEngine : public RenderEngine {
    GLuint mProtectedTexName;
    GLint mMaxViewportDims[2];
    GLint mMaxTextureSize;

    virtual void bindImageAsFramebuffer(EGLImageKHR image,
            uint32_t* texName, uint32_t* fbName, uint32_t* status);
    virtual void unbindFramebuffer(uint32_t texName, uint32_t fbName);

public:
    GLES11RenderEngine(uint32_t featureFlags);

    virtual ~GLES11RenderEngine();

protected:
    virtual void dump(String8& result);
    virtual void setViewportAndProjection(size_t vpw, size_t vph,
            Rect sourceCrop, size_t hwh, bool yswap,
            Transform::orientation_flags rotation);
#ifdef USE_HWC2
    virtual void setupLayerBlending(bool premultipliedAlpha, bool opaque, bool disableTexture, const half4& color)  override;
    virtual void setupDimLayerBlending(float alpha) ;
    // Color management related functions and state
    virtual void setColorMode(__attribute__((unused)) android_color_mode mode){};
    virtual void setSourceDataSpace( __attribute__((unused)) ui::Dataspace  source){};
    virtual void setOutputDataSpace( __attribute__((unused)) ui::Dataspace  dataspace){};
    virtual void setDisplayMaxLuminance(__attribute__((unused))const float maxLuminance){};
    virtual void setWideColor(__attribute__((unused)) bool hasWideColor){};
    virtual bool usesWideColor(){return false;};
    virtual void setSourceY410BT2020(__attribute__((unused)) bool enable) {};
#else
    virtual void setupLayerBlending(bool premultipliedAlpha, bool opaque,
            int alpha);
    virtual void setupDimLayerBlending(int alpha);
#endif
    virtual void setupLayerTexturing(const Texture& texture);
    virtual void setupLayerBlackedOut();
    virtual void setupFillWithColor(float r, float g, float b, float a) ;
	virtual void setupColorTransform(__attribute__((unused)) const mat4& colorTransform){};
    virtual void setSaturationMatrix(__attribute__((unused)) const mat4& saturationMatrix){};
    virtual void disableTexturing();
    virtual void disableBlending();

    virtual void drawMesh(const Mesh& mesh);

    virtual size_t getMaxTextureSize() const;
    virtual size_t getMaxViewportDims() const;
};
}
}

// ---------------------------------------------------------------------------
}; // namespace android
// ---------------------------------------------------------------------------

#endif /* SF_GLES11RENDERENGINE_H_ */
