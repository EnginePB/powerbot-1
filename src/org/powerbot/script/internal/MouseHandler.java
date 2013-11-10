package org.powerbot.script.internal;

import java.applet.Applet;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.concurrent.TimeUnit;

import org.powerbot.client.Client;
import org.powerbot.client.input.Mouse;
import org.powerbot.util.math.HeteroMouse;
import org.powerbot.util.math.Vector3;

public class MouseHandler {
	private static final int MAX_ATTEMPTS = 5;
	public final MouseSimulator simulator;
	private final Applet applet;
	private final Client client;

	public MouseHandler(final Applet applet, final Client client) {
		this.applet = applet;
		this.client = client;
		simulator = new HeteroMouse();
	}

	public void click(final int x, final int y, final int button) {
		try {
			Thread.sleep(simulator.getPressDuration());
		} catch (InterruptedException ignored) {
		}
		press(x, y, button);
		try {
			Thread.sleep(simulator.getPressDuration());
		} catch (InterruptedException ignored) {
		}
		release(x, y, button);
		try {
			Thread.sleep(simulator.getPressDuration());
		} catch (InterruptedException ignored) {
		}
	}

	public void scroll(boolean down) {
		final Mouse mouse;
		if ((mouse = client.getMouse()) == null) {
			return;
		}
		if (!mouse.isPresent()) {
			return;
		}
		final Component target = getSource();
		Point location = getLocation();
		mouse.sendEvent(new MouseWheelEvent(target, MouseWheelEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, location.x, location.y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, down ? 1 : -1));
	}

	public void press(final int x, final int y, final int button) {
		final Mouse mouse;
		if ((mouse = client.getMouse()) == null) {
			return;
		}
		if (!mouse.isPresent() || mouse.isPressed()) {
			return;
		}
		final Component target = getSource();
		mouse.sendEvent(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, x, y, 1, false, button));
	}

	public void release(final int x, final int y, final int button) {
		final Mouse mouse;
		if ((mouse = client.getMouse()) == null) {
			return;
		}
		if (!mouse.isPressed()) {
			return;
		}
		final long mark = System.currentTimeMillis();
		final Component target = getSource();
		mouse.sendEvent(new MouseEvent(target, MouseEvent.MOUSE_RELEASED, mark, 0, x, y, 1, false, button));
		if (mouse.getPressX() == mouse.getX() && mouse.getPressY() == mouse.getY()) {
			mouse.sendEvent(new MouseEvent(target, MouseEvent.MOUSE_CLICKED, mark, 0, x, y, 1, false, button));
		}
	}

	public void move(final int x, final int y) {
		final long mark = System.currentTimeMillis();
		final Component target = getSource();
		final Mouse mouse;
		if ((mouse = client.getMouse()) == null) {
			return;
		}
		final boolean present = x >= 0 && y >= 0 && x < target.getWidth() && y < target.getHeight();
		if (!mouse.isPresent() && present) {
			mouse.sendEvent(new MouseEvent(target, MouseEvent.MOUSE_ENTERED, mark, 0, x, y, 0, false));
		}
		if (mouse.isPressed()) {
			mouse.sendEvent(new MouseEvent(target, MouseEvent.MOUSE_DRAGGED, mark, 0, x, y, 0, false));
		} else if (present) {
			mouse.sendEvent(new MouseEvent(target, MouseEvent.MOUSE_MOVED, mark, 0, x, y, 0, false));
		} else if (mouse.isPresent()) {
			mouse.sendEvent(new MouseEvent(target, MouseEvent.MOUSE_EXITED, mark, 0, x, y, 0, false));
		}
	}

	public synchronized void handle(MouseTarget target) {
		final Mouse mouse = client.getMouse();
		if (target == null || mouse == null) {
			return;
		}

		start:
		for (; ; ) {
			if (++target.steps > MAX_ATTEMPTS) {
				target.failed = true;
				break;
			}

			final Point loc = mouse.getLocation();
			if (target.curr == null) {
				target.curr = new Vector3(loc.x, loc.y, 255);
			}
			target.curr.x = loc.x;
			target.curr.y = loc.y;
			if (target.dest == null) {
				final Point p = target.targetable.getInteractPoint();
				target.dest = new Vector3(p.x, p.y, 0);
			}
			if (target.dest.x == -1 || target.dest.y == -1) {
				target.failed = true;
				break;
			}
			final Vector3 curr = target.curr;
			final Vector3 dest = target.dest;

			final Point centroid = target.targetable.getCenterPoint();
			long m;
			final Iterable<Vector3> spline = simulator.getPath(curr, dest);
			for (final Vector3 v : spline) {
				move(v.x, v.y);
				curr.x = v.x;
				curr.y = v.y;
				curr.z = v.z;

				m = System.nanoTime();
				final double traverseLength = Math.sqrt(Math.pow(dest.x - curr.x, 2) + Math.pow(dest.y - curr.y, 2));
				final double mod = 2.5 + Math.sqrt(Math.pow(dest.x - centroid.x, 2) + Math.pow(dest.y - centroid.y, 2));
				if (traverseLength < mod) {
					final Point pos = curr.to2DPoint();
					if (target.targetable.contains(pos) && target.filter.accept(pos)) {
						if (target.execute(this)) {
							break start;
						}
					}
				}
				m = System.nanoTime() - m;

				final long l = TimeUnit.NANOSECONDS.toMillis(simulator.getAbsoluteDelay(v.z) - m);
				if (l > 0) {
					try {
						Thread.sleep(l);
					} catch (InterruptedException ignored) {
					}
				}
			}

			final Point next = target.targetable.getNextPoint();
			target.dest = new Vector3(next.x, next.y, 0);
		}
	}

	public Component getSource() {
		return applet.getComponentCount() > 0 ? applet.getComponent(0) : null;
	}

	public Point getLocation() {
		final Mouse mouse;
		if ((mouse = client.getMouse()) == null) {
			return new Point(-1, -1);
		}
		return mouse.getLocation();
	}
}
