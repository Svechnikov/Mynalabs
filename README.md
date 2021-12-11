# Test app adding pulsating watermark to recorded video

![alt text](https://github.com/svechnikov/Mynalabs/blob/master/demo/screenshot.png?raw=true)

App screen record video example is [here](https://github.com/svechnikov/Mynalabs/blob/master/demo/recorded-video.mp4?raw=true).

Recorded video example is [here](https://github.com/svechnikov/Mynalabs/blob/master/demo/screenrecord-video.mp4?raw=true).

Debug APK is [here](https://github.com/svechnikov/Mynalabs/blob/master/demo/svechnikov.mynalabs.apk?raw=true).

## Task

### Requirements

Your task is to implement a screen with a camera and a record button. User pressed the button - the recording began, released - the recording ended and the user sees what was recorded.

The app should consist of one screen:

- Users can record (self camera implementation)
- In the recorded file, the video should be watermarked
- There should not be a watermark on the preview from the camera
- The watermark should be pulsating
- The pulsating frequency and amplitude should be easily regulated, at least by a variable in the code
- Zero-dependency — the app should not use any third-party libraries (camera2 or cameraX of course you can, ExoPlayer too)

### Evaluation

- It is important that the UI in your app works smoothly and without lagging
- Any additional functionality that makes the product better, is welcome!
- Optimizations related to video conversion and watermark rendering are welcome too

## Camera handling

Several ways were possible:
 - `Camera` API
 - `Camera2` API
 - `Camerax`

Old `Camera` API is easy to use, but contains a lot of issues related to how it behaves on different devices, therefore it got deprecated in favor of `Camera2`, we shouldn't use it unless we target API < 21 devices.

`Camera2` is more difficult to work with, compared to `Camera`, in my opinion, therefore I chose `Camerax` which is a wrapper for `Camera2`.

One issue with `Camerax` I didn't resolve is how to get current camera frame rate mode, therefore I hardcoded 30 FPS when encoding.

Also, there seems to be no way to specify target resolution for `Preview` use case, which was used. But it shouldn't be a problem, since at least 1080 should be chosen by the library, according to the documentation.

If we need to have larger resolution, we might create our own use case (which would also help with the FPS issue).

## Watermark drawing approach

A couple of ways were possible:
 - Less performant, involving full bitmap copying of each frame between GPU and CPU
 - More performant, involving all work done on GPU using OpenGL ES

I chose to use OpenGL ES.

All frames from camera are directed to an off-screen surface, backed by a GLES texture. This texture is then drawn to 2 other surfaces:
 - Preview surface, which was received from `SurfaceView`
 - Encoder surface, which was received from `MediaCodec`

Encoder surface additionally receives a simple texture on top, representing the watermark. The watermark pulsation is controlled by fragment shader: a float variable for alpha channel is passed to it, which is set each time before drawing.

The pulsation itself is controlled by sine function and some constant values, defined in `WatermarkConfig` for easy tweaking, as requested in the requirements.

Pulsation depends on device time, it seems to be working fine, but we might also consider using video PTS values.

## Encoding

Only video is encoded.

I used `MediaCodec`. Each time a frame from camera's `TextureView` is available, it's rendered into the encoder surface to be encoded.

Video is encoded with h264 codec ("video/avc") at frame resolution.

Audio isn't encoded.

The stream is muxed into MPEG4 (mp4) container.

## UI

UI is quite simple. It has 3 states:

 - Permissions state
 - Preview state
 - Playback state

When in preview mode, a red "record" button is displayed. Holding it starts video recording.

After holding it for at least 2 seconds, recorded playback is shown and the button is replaced with "close" button.

Close button returns to Preview state.

## TODO

 - Use android resources instead of hardcoded values (strings/colors/dimensions)
 - Handle screen rotation instead of locking the screen
 - Improve the UI
 - Review and improve "camera - renderer - encoder" data flow
 - Check the code for crashes