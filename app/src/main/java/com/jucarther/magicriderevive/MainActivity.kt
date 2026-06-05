package com.jucarther.magicriderevive

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.*
import kotlin.random.Random

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(MagicRideView(this))
    }
}

class MagicRideView(private val ctx: Context) : SurfaceView(ctx), SurfaceHolder.Callback, Runnable {
    private var running = false
    private var thread: Thread? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rng = Random(13)
    private val prefs: SharedPreferences = ctx.getSharedPreferences("magic_ride_scores", Context.MODE_PRIVATE)

    private var w = 1
    private var h = 1
    private var state = State.MENU
    private var mode = Mode.COMPETITION
    private var witchY = 0f
    private var velocity = 0f
    private var distance = 0f
    private var best = prefs.getInt("best_julio", 0)
    private var friendBest = prefs.getInt("best_amiga", 1450).coerceAtLeast(1450)
    private var friendGhost = friendBest.toFloat() * .42f
    private var speed = 500f
    private var energy = 100f
    private var fever = 0f
    private var spawnTimer = 0f
    private var gemTimer = 0f
    private var anim = 0f
    private var roomCode = prefs.getString("room_code", "PASEO2026") ?: "PASEO2026"

    private val obstacles = mutableListOf<Thing>()
    private val gems = mutableListOf<Thing>()
    private val clouds = mutableListOf<Thing>()
    private val trails = mutableListOf<Thing>()

    enum class State { MENU, PLAYING, PAUSED, GAME_OVER, RANKING }
    enum class Mode { SOLO, COMPETITION }
    data class Thing(var x: Float, var y: Float, var r: Float, var type: Int)

    init { holder.addCallback(this); isFocusable = true }

    override fun surfaceCreated(holder: SurfaceHolder) { running = true; thread = Thread(this, "MagicRideLoop"); thread!!.start() }
    override fun surfaceDestroyed(holder: SurfaceHolder) { running = false; thread?.join(700) }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        w = width; h = height
        if (witchY == 0f) witchY = h * 0.52f
        clouds.clear()
        repeat(16) { clouds.add(Thing(rng.nextInt(0, max(10, w)).toFloat(), rng.nextInt(40, max(80, h / 2)).toFloat(), rng.nextInt(25, 82).toFloat(), 0)) }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN) return true
        val x = e.x; val y = e.y
        when (state) {
            State.MENU -> {
                if (y > h * .62f && y < h * .72f) { mode = Mode.COMPETITION; startGame() }
                else if (y > h * .74f && y < h * .84f) { mode = Mode.SOLO; startGame() }
                else if (y > h * .86f) state = State.RANKING
                else startGame()
            }
            State.RANKING -> state = State.MENU
            State.GAME_OVER -> if (y > h * .76f) state = State.RANKING else startGame()
            State.PAUSED -> state = State.PLAYING
            State.PLAYING -> { velocity = -700f; energy = min(100f, energy + 2.5f); trails.add(Thing(w*.24f - 52, witchY + 16, 8f, 0)) }
        }
        return true
    }

    private fun startGame() {
        obstacles.clear(); gems.clear(); trails.clear(); distance = 0f; speed = 500f; velocity = 0f; energy = 100f; fever = 0f
        witchY = h * 0.52f; friendGhost = (friendBest * .35f).coerceAtLeast(480f); spawnTimer = .65f; gemTimer = .9f; state = State.PLAYING
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime(); val dt = ((now - last) / 1_000_000_000f).coerceAtMost(.033f); last = now
            if (state == State.PLAYING) update(dt)
            drawFrame(); Thread.sleep(16)
        }
    }

    private fun update(dt: Float) {
        anim += dt; distance += dt * 124f; speed += dt * 12.5f; energy -= dt * 3.8f; if (fever > 0f) fever -= dt
        if (mode == Mode.COMPETITION) friendGhost += dt * (86f + sin(anim*.7f)*26f)
        velocity += 1185f * dt; witchY += velocity * dt
        if (witchY < 55f) { witchY = 55f; velocity = 0f }
        if (witchY > h - 56f || energy <= 0f) crash()
        clouds.forEach { it.x -= (speed * .08f + it.r * .3f) * dt; if (it.x < -130) { it.x = w + rng.nextInt(20, 240); it.y = rng.nextInt(35, max(80, h / 2)).toFloat() } }
        trails.forEach { it.x -= speed * dt; it.r += dt * 16f }
        trails.removeAll { it.x < -40 || it.r > 36 }
        spawnTimer -= dt
        if (spawnTimer <= 0f) { val y = rng.nextInt(90, max(120, h - 105)).toFloat(); obstacles.add(Thing(w + 95f, y, rng.nextInt(28, 56).toFloat(), rng.nextInt(0, 3))); spawnTimer = rng.nextDouble(.43, .90).toFloat().coerceAtLeast(.36f) }
        gemTimer -= dt
        if (gemTimer <= 0f) { val y = rng.nextInt(85, max(110, h - 95)).toFloat(); gems.add(Thing(w + 80f, y, 18f, 0)); gemTimer = rng.nextDouble(.72, 1.30).toFloat() }
        val witch = RectF(w * .24f - 45, witchY - 32, w * .24f + 62, witchY + 31)
        val oit = obstacles.iterator()
        while (oit.hasNext()) { val o = oit.next(); o.x -= speed * dt; if (o.x < -80) oit.remove(); val box = RectF(o.x-o.r, o.y-o.r, o.x+o.r, o.y+o.r); if (RectF.intersects(witch, box)) { if (fever > 0f) oit.remove() else crash() } }
        val git = gems.iterator()
        while (git.hasNext()) { val g = git.next(); g.x -= speed * dt * 1.05f; if (g.x < -40) git.remove(); if (hypot((g.x - w*.24f).toDouble(), (g.y - witchY).toDouble()) < 52) { distance += 150f; energy = min(100f, energy + 13f); fever = max(fever, 1.25f); git.remove() } }
    }

    private fun crash() {
        val score = distance.toInt(); if (score > best) { best = score; prefs.edit().putInt("best_julio", best).apply() }
        if (mode == Mode.COMPETITION && score > friendBest - 250) { friendBest = max(friendBest, score + rng.nextInt(80, 420)); prefs.edit().putInt("best_amiga", friendBest).apply() }
        state = State.GAME_OVER
    }

    private fun drawFrame() { val c = holder.lockCanvas() ?: return; drawGame(c); holder.unlockCanvasAndPost(c) }

    private fun drawGame(c: Canvas) {
        w = c.width; h = c.height
        p.shader = LinearGradient(0f,0f,0f,h.toFloat(), Color.rgb(18,4,60), Color.rgb(32,92,126), Shader.TileMode.CLAMP); c.drawRect(0f,0f,w.toFloat(),h.toFloat(),p); p.shader = null
        drawMoonAndStars(c); drawClouds(c); drawForest(c); drawTrails(c); drawGems(c); drawObstacles(c); if (mode == Mode.COMPETITION) drawFriendGhost(c); drawWitch(c, w*.24f, witchY, false); drawHud(c)
        if (state == State.RANKING) drawRanking(c) else if (state != State.PLAYING) drawOverlay(c)
    }

    private fun drawMoonAndStars(c: Canvas) { p.color = Color.rgb(255,239,175); c.drawCircle(w-92f,90f,48f,p); p.color=Color.argb(170,255,255,255); repeat(78) { i -> val x=((i*83 + distance*.35f)%(w+90)).toFloat(); val y=(30+(i*47)%max(80,h/2)).toFloat(); c.drawCircle(x,y, if(i%9==0)3f else 1.5f,p) } }
    private fun drawClouds(c: Canvas) { p.color=Color.argb(70,255,255,255); clouds.forEach { c.drawOval(RectF(it.x-it.r,it.y-it.r*.35f,it.x+it.r*1.5f,it.y+it.r*.45f),p) } }
    private fun drawForest(c: Canvas) { p.color=Color.rgb(9,35,42); for(i in 0..12){ val x=i*w/10f-(distance*.18f%150f); val base=h-45f; c.drawCircle(x,base,90f,p); c.drawCircle(x+65,base-28,118f,p)}; p.color=Color.rgb(16,55,55); c.drawRect(0f,h-42f,w.toFloat(),h.toFloat(),p) }
    private fun drawTrails(c: Canvas) { trails.forEach { p.color=Color.argb((180-it.r*4).toInt().coerceIn(20,180),255,210,80); c.drawCircle(it.x,it.y,it.r,p) } }
    private fun drawObstacles(c: Canvas) { obstacles.forEach { o -> when(o.type){ 0->{p.color=Color.rgb(35,16,60); c.drawOval(RectF(o.x-o.r,o.y-o.r*.45f,o.x+o.r,o.y+o.r*.45f),p); p.color=Color.rgb(235,70,60); c.drawCircle(o.x+o.r*.25f,o.y-o.r*.05f,5f,p)} 1->{p.color=Color.rgb(98,45,35); c.drawCircle(o.x,o.y,o.r,p); p.color=Color.rgb(255,150,50); c.drawCircle(o.x-6,o.y-4,o.r*.35f,p)} else->{p.color=Color.rgb(30,15,45); val path=Path(); path.moveTo(o.x,o.y-o.r); path.lineTo(o.x+o.r,o.y+o.r); path.lineTo(o.x-o.r,o.y+o.r); path.close(); c.drawPath(path,p)}} } }
    private fun drawGems(c: Canvas) { p.color=Color.rgb(255,219,70); gems.forEach { val path=Path(); path.moveTo(it.x,it.y-it.r); path.lineTo(it.x+it.r,it.y); path.lineTo(it.x,it.y+it.r); path.lineTo(it.x-it.r,it.y); path.close(); c.drawPath(path,p) } }
    private fun drawFriendGhost(c: Canvas) { val gx = w*.24f + ((friendGhost - distance).coerceIn(-500f,500f) / 500f) * (w*.38f); val gy = h*.52f + sin(anim*2.1f)*42f; p.alpha=120; drawWitch(c,gx,gy,true); p.alpha=255; p.textSize=24f; p.color=Color.rgb(230,210,255); p.textAlign=Paint.Align.CENTER; c.drawText("Amiga", gx, gy-72f, p) }
    private fun drawWitch(c: Canvas, x: Float, y: Float, ghost: Boolean) { val bob=sin(anim*9f)*5f; p.strokeWidth=8f; p.strokeCap=Paint.Cap.ROUND; p.color=if(ghost) Color.rgb(130,110,190) else Color.rgb(126,72,31); c.drawLine(x-78,y+23+bob,x+85,y+4+bob,p); p.color=if(ghost) Color.rgb(92,58,140) else Color.rgb(55,24,92); c.drawOval(RectF(x-30,y-17+bob,x+35,y+36+bob),p); p.color=Color.rgb(246,205,150); c.drawCircle(x+34,y-8+bob,14f,p); p.color=Color.rgb(28,17,45); c.drawCircle(x+24,y-14+bob,20f,p); p.color=if(ghost) Color.rgb(150,105,210) else Color.rgb(108,54,160); val hat=Path(); hat.moveTo(x+15,y-27+bob); hat.lineTo(x+43,y-75+bob); hat.lineTo(x+56,y-20+bob); hat.close(); c.drawPath(hat,p); c.drawOval(RectF(x+2,y-27+bob,x+66,y-13+bob),p); p.color=Color.argb(if(fever>0&&!ghost)230 else 90,255,205,65); c.drawCircle(x-45,y+16+bob, if(fever>0&&!ghost)18f else 9f,p) }
    private fun drawHud(c: Canvas) { p.typeface=Typeface.DEFAULT_BOLD; p.textSize=34f; p.color=Color.WHITE; p.textAlign=Paint.Align.LEFT; c.drawText("Magic Ride Reborn",24f,42f,p); p.textSize=27f; c.drawText("Distancia ${distance.toInt()}   Mejor $best",24f,76f,p); p.color=Color.argb(160,0,0,0); c.drawRoundRect(RectF(24f,92f,224f,116f),12f,12f,p); p.color=Color.rgb(255,210,74); c.drawRoundRect(RectF(26f,94f,26f+196f*(energy/100f),114f),10f,10f,p); if(mode==Mode.COMPETITION){ val maxScore=max(max(best,friendBest), distance.toInt()).coerceAtLeast(2000); p.textSize=22f; p.color=Color.WHITE; c.drawText("Sala $roomCode", w-210f, 42f, p); drawBar(c,w-230f,62f,200f,"Julio",distance.toInt(),maxScore); drawBar(c,w-230f,94f,200f,"Amiga",friendGhost.toInt(),maxScore) } }
    private fun drawBar(c:Canvas, x:Float, y:Float, bw:Float, name:String, score:Int, maxScore:Int){ p.color=Color.argb(145,0,0,0); c.drawRoundRect(RectF(x,y,x+bw,y+18),9f,9f,p); p.color=if(name=="Julio") Color.rgb(255,210,74) else Color.rgb(172,130,255); c.drawRoundRect(RectF(x,y,x+bw*(score.toFloat()/maxScore).coerceIn(0f,1f),y+18),9f,9f,p); p.color=Color.WHITE; p.textSize=18f; c.drawText("$name $score m",x,y-3,p) }
    private fun drawOverlay(c: Canvas) { p.color=Color.argb(178,0,0,0); c.drawRect(0f,0f,w.toFloat(),h.toFloat(),p); p.textAlign=Paint.Align.CENTER; p.typeface=Typeface.DEFAULT_BOLD; p.color=Color.WHITE; if(state==State.MENU){ p.textSize=60f; c.drawText("Magic Ride Reborn",w/2f,h*.28f,p); p.textSize=28f; c.drawText("Bruja en escoba, estrellas, bosque y competencia",w/2f,h*.37f,p); drawButton(c,"Competir con amiga",h*.64f); drawButton(c,"Jugar solo",h*.76f); drawButton(c,"Ver ranking",h*.88f) } else if(state==State.GAME_OVER){ p.textSize=54f; c.drawText("Fin del paseo",w/2f,h*.30f,p); p.textSize=30f; c.drawText("Puntaje: ${distance.toInt()}  |  Mejor: $best",w/2f,h*.39f,p); if(mode==Mode.COMPETITION) c.drawText(if(distance.toInt()>=friendGhost.toInt()) "Vas ganando la sala" else "Tu amiga va adelante",w/2f,h*.46f,p); drawButton(c,"Reintentar",h*.63f); drawButton(c,"Ranking",h*.78f) } }
    private fun drawButton(c:Canvas, text:String, cy:Float){ p.color=Color.argb(190,70,35,115); c.drawRoundRect(RectF(w*.18f,cy-40,w*.82f,cy+40),25f,25f,p); p.color=Color.rgb(255,230,140); p.textSize=30f; p.textAlign=Paint.Align.CENTER; c.drawText(text,w/2f,cy+11,p) }
    private fun drawRanking(c:Canvas){ p.color=Color.argb(214,0,0,0); c.drawRect(0f,0f,w.toFloat(),h.toFloat(),p); p.textAlign=Paint.Align.CENTER; p.typeface=Typeface.DEFAULT_BOLD; p.color=Color.WHITE; p.textSize=54f; c.drawText("Ranking de la sala",w/2f,h*.22f,p); p.textSize=28f; c.drawText("Código: $roomCode",w/2f,h*.30f,p); val rows=listOf("Julio" to best, "Amiga" to friendBest, "Lina" to 4820, "Mario" to 3900, "Sofía" to 3300).sortedByDescending{it.second}; p.textAlign=Paint.Align.LEFT; p.textSize=30f; rows.take(5).forEachIndexed{ i,r -> p.color=if(i==0) Color.rgb(255,225,85) else Color.WHITE; c.drawText("${i+1}. ${r.first}",w*.22f,h*.42f+i*48,p); p.textAlign=Paint.Align.RIGHT; c.drawText("${r.second} m",w*.78f,h*.42f+i*48,p); p.textAlign=Paint.Align.LEFT }; p.textAlign=Paint.Align.CENTER; p.textSize=24f; p.color=Color.rgb(215,215,230); c.drawText("Toca para volver",w/2f,h*.86f,p) }
}
