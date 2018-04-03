package simplefx

/** A wrapper with utils for simpler access to javafx */
object Fx {
  @volatile var isDebug = true
  def debug[T](a: T) = if (isDebug) println(a.toString)

  @volatile private var _primaryStage: javafx.stage.Stage = _
  def primaryStage: javafx.stage.Stage = _primaryStage

  @volatile private var delayedAppInit: javafx.stage.Stage => Unit = _

  class FXState {
    val Unstarted = 0
    val Starting  = 1
    val Started   = 2

    private var state = Unstarted

    def attemptStart(): Boolean = this.synchronized {
      if (state == Unstarted) { // is this the first time asking
        state = Starting
        true
      } else {
        waitUntilStarted() // you have to wait for toolkit to start
        false
      }
    }

    def waitUntilStarted(): Unit = this.synchronized {
      while (state != Started) wait()
    }

    def hasStarted(): Unit = this.synchronized {
      state = Started
      notifyAll
    }
  }

  private val fxState = new FXState

  private def launchApp(initPrimaryStage: javafx.stage.Stage => Unit): Unit = {
    delayedAppInit = initPrimaryStage  // only assigned once here
    new Thread( () => {
      javafx.application.Application.launch(classOf[UnderlyingApp]) // blocks until exit
    }).start
  }

  def runInFxThread(block: => Unit): Unit =
    javafx.application.Platform.runLater { () => block }

  def apply(block: => Unit): Unit = runInFxThread(block)

  /** Creates a new window and at first call launches the application. */
  def mkStage(init: javafx.stage.Stage => Unit): javafx.stage.Stage =
    if (fxState.attemptStart) {
      val t0 = System.nanoTime
      launchApp(init)
      fxState.waitUntilStarted()  // blocks until UnderlyingApp.start is called
      debug(s"JavaFX Toolkit launched in ${(System.nanoTime - t0)/1000000} ms")
      primaryStage
    } else {
      val ready = new java.util.concurrent.CountDownLatch(1)
      var nonPrimaryStage: javafx.stage.Stage = null
      val t0 = System.nanoTime
      runInFxThread {
        nonPrimaryStage = new javafx.stage.Stage;
        init(nonPrimaryStage)
        ready.countDown
      }
      ready.await
      debug(s"JavaFX stage constructed in ${(System.nanoTime - t0)/1000000} ms")
      nonPrimaryStage
    }

  private class UnderlyingApp extends javafx.application.Application {
    override def start(primaryStage: javafx.stage.Stage): Unit = {
      _primaryStage = primaryStage  // only assigned once here
      delayedAppInit(primaryStage)  // only called once here
      javafx.application.Platform.setImplicitExit(false) // dont exit javafx ehrn app closed
      fxState.hasStarted  // release all threads waiting for toolkit to start
    }
    override def stop(): Unit = {
      debug("JavaFX Toolkit Application stopped.")
    }
  }

  def menuItem(
    name: String,
    shortcut: String = "",
    action: () => Unit
  ): javafx.scene.control.MenuItem = {
    val item = javafx.scene.control.MenuItemBuilder.create()
      .text(name)
      .onAction(e => action())
      .build()
    if (shortcut.nonEmpty) {
      item.setAccelerator(javafx.scene.input.KeyCombination.keyCombination(shortcut))
    }
    item
  }

  def menu(name: String, items: javafx.scene.control.MenuItem*): javafx.scene.control.Menu = {
    val menu = new javafx.scene.control.Menu(name)
    menu.getItems.addAll(items: _*)
    menu
  }

  def menuBar(items: javafx.scene.control.Menu*) = new javafx.scene.control.MenuBar(items:_*)

  def stop(): Unit = javafx.application.Platform.exit
}
