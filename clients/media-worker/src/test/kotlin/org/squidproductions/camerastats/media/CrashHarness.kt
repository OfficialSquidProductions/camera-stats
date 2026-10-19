package org.squidproductions.camerastats.media

import java.nio.file.Path

/** An actual abrupt subprocess exit: no exception unwinding, close(), or shutdown hooks. */
fun main(args:Array<String>) {
    val repo=MediaRepository(Path.of(args[0]));val service=MediaService(repo,Decoder(Path.of(args[2]),Path.of(args[3])))
    val engine=CopyJobs(service) {point,_->if(point==args[4]) Runtime.getRuntime().halt(73)}
    if(args[4].startsWith("undo")) engine.undo(args[1]) else engine.execute(args[1])
    error("Crash checkpoint was not reached")
}
