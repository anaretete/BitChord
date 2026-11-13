package expo.modules.glassmenuandroid.render

internal class GlassRecipe(
  val saturation: Float,
  val black: Float,
  val white: Float,
  val rollOff: Float,
  val fillAlpha: Float,
  val tint: Float = 0f,
  val tintAlpha: Float = 0f,
) {
  companion object {
    val ControlDark = GlassRecipe(1.3f, .16f, 1.125f, .38f, .10f, tint = .18f, tintAlpha = .28f)
    val MenuDark = GlassRecipe(1.3f, .18f, 1.125f, .42f, .14f, tint = .18f, tintAlpha = .35f)
    val ControlLight = GlassRecipe(1.2f, .46f, 1.05f, 0f, .22f)
    val MenuLight = GlassRecipe(1.2f, .48f, 1.05f, 0f, .26f, tint = .98f, tintAlpha = .32f)
  }
}
