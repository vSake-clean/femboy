package pl.vsake.femboy.acc;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import pl.vsake.femboy.pat.PatRenderState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wszystkie dodatki jako jeden model gracza (jak uszy deadmau5 w vanilli): szkielet głowy, tułowia, rąk i nóg
 * bez własnych kostek, a do niego przyczepione uszy, ogony, obroże itd. Model układa się dokładnie jak gracz
 * (chodzenie, skradanie, pływanie, ściśnięcie głowy przy głaskaniu), a dodatki dostają jeszcze swój ruch z Anim.
 * Widać tylko te elementy, które są w wyglądzie danej osoby.
 */
public final class AccessoryModel extends PlayerEntityModel {
	private final boolean slim;
	/** Część modelu dla każdego elementu z Parts.PIECES (ten sam indeks). */
	private final ModelPart[] parts;
	private final Anim.In in = new Anim.In();
	private final float[] delta = new float[6];

	private AccessoryModel(ModelPart root, boolean slim) {
		super(root, slim);
		this.slim = slim;
		List<Parts.Piece> pieces = Parts.PIECES;
		parts = new ModelPart[pieces.size()];
		Map<String, ModelPart> made = new HashMap<>();
		for (int i = 0; i < pieces.size(); i++) {
			Parts.Piece p = pieces.get(i);
			ModelPart parent = p.parent != null ? made.get(p.parent.name) : anchor(p.anchor);
			ModelPart part = parent.getChild(p.name);
			made.put(p.name, part);
			parts[i] = part;
		}
	}

	/** @param slim chude ręce (3 px) – łapki mają wtedy inny kształt */
	public static AccessoryModel create(boolean slim) {
		ModelData data = PlayerEntityModel.getTexturedModelData(Dilation.NONE, slim);
		ModelPartData root = data.getRoot().resetChildrenParts(); // zostaje szkielet (głowa, tułów…), znikają kostki
		Map<String, ModelPartData> made = new HashMap<>();
		for (Parts.Piece p : Parts.PIECES) {
			ModelPartData parent = p.parent != null ? made.get(p.parent.name) : root.getChild(p.anchor.part);
			ModelPartBuilder b = ModelPartBuilder.create();
			for (Parts.Box box : p.boxes)
				b.uv(box.u, box.v).cuboid(box.x, box.y, box.z, box.sx, box.sy, box.sz, new Dilation(box.gx, box.gy, box.gz));
			made.put(p.name, parent.addChild(p.name, b, ModelTransform.of(p.ox, p.oy, p.oz, p.pitch, p.yaw, p.roll)));
		}
		return new AccessoryModel(TexturedModelData.of(data, Parts.TEX_W, Parts.TEX_H).createModel(), slim);
	}

	private ModelPart anchor(Parts.Anchor a) {
		return switch (a) {
			case HEAD -> head;
			case BODY -> body;
			case RIGHT_ARM -> rightArm;
			case LEFT_ARM -> leftArm;
			case RIGHT_LEG -> rightLeg;
			case LEFT_LEG -> leftLeg;
		};
	}

	@Override
	public void setAngles(PlayerEntityRenderState state) {
		// poza gracza; przy okazji wszystkie nasze części wracają do pozy spoczynkowej z Parts
		super.setAngles(state);
		Look look = ((AccessoryRenderState) state).femboy$getLook();
		if (look != null) {
			in.age = state.age;
			in.limbSwing = state.limbSwingAnimationProgress;
			in.limbAmount = state.limbSwingAmplitude;
			in.sneaking = state.isInSneakingPose;
			in.riding = state.hasVehicle;
			in.swimming = state.isSwimming || state.isGliding;
			in.helmet = state.equippedHeadStack != null && !state.equippedHeadStack.isEmpty();
			in.pat = ((PatRenderState) state).femboy$getPatTime();
			in.rightLegPitch = rightLeg.pitch;
			in.leftLegPitch = leftLeg.pitch;
		}
		List<Parts.Piece> pieces = Parts.PIECES;
		for (int i = 0; i < parts.length; i++) {
			Parts.Piece p = pieces.get(i);
			ModelPart part = parts[i];
			part.visible = look != null && p.visible(look, slim);
			if (!part.visible || p.motion == Parts.Motion.NONE) continue;
			Anim.apply(p, look, in, delta);
			part.pitch += delta[0];
			part.yaw += delta[1];
			part.roll += delta[2];
			part.originX += delta[3];
			part.originY += delta[4];
			part.originZ += delta[5];
		}
	}
}
