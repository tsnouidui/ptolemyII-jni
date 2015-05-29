within ;
model feedback
  Real x( start = 0.0);
  Modelica.Blocks.Interfaces.RealInput u1;
  Modelica.Blocks.Interfaces.RealInput u2;
  Modelica.Blocks.Interfaces.RealOutput y;
equation
   der( x) = u2 - x;
   y  = u1;
  annotation (uses(Modelica(version="3.2.1")));
end feedback;
