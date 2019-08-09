This repository contains an example application using SoundWow module, the module it self is placed in `soundwow` directory.

You can run automated instrumental tests by running the SoundWowInstrumentalTest class (in `src/androidTest`)

To include the module in your application just import the soundwow directoy using AndroidStudio `New -> Import Module`

To use it just needs a sound view reference and call `setSound` passing a `File` or `AssetFileDescriptor`, optionally you can set `seekListener` to receive seeking events

There is a list of accessible properties
`soundInfo` a class containing the loaded sound info or null if there is no sound loaded
`progress` a float between 0.0 and 1.0 representing the sound view progress
`seekListener` a interface to interact with seek events
`displayNegative` a boolean to enable negative samples, if false the bottom part of the sound wave is not displayed and wave is aligned to the bottom
`blockMargin` a int representing pixel separation between blocks
`blockSize` a int representing pixel block size
`blockMinHeight` a int representing block minimum height in pixels
`playerBlockColor` a int color used to paint the played part of the sound
`notPlayedBlockColor` a int color used to paint the not played part of the sound

Methods
`setSound(file: File)` load sound file
`setSound(assetFileDescriptor: AssetFileDescriptor)` load sound asset

Interfaces
```
OnSeekListener {
    // all methods receive a value between 0.0 and 1.0 representing the selected progress
    fun onSeekBegin(progress: Float) // fired when user long tap to drag the progress
    fun onSeekUpdate(progress: Float) // fired when user is dragging
    fun onSeekComplete(progress: Float) // fired on tap or when dragginf is released
}
```

Custom attributes for XML view
<attr name="display_negative" format="boolean"/>
<attr name="block_margin" format="integer"/>
<attr name="block_size" format="integer"/>
<attr name="block_min_height" format="integer"/>
<attr name="played_block_color" format="color"/>
<attr name="not_played_block_color" format="color"/>