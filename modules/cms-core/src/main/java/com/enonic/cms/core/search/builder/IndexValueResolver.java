package com.enonic.cms.core.search.builder;

import org.apache.commons.lang.StringUtils;

import com.google.common.base.Function;

import com.enonic.cms.core.content.index.queryexpression.ArrayExpr;
import com.enonic.cms.core.content.index.queryexpression.Expression;
import com.enonic.cms.core.content.index.queryexpression.FunctionEvaluator;
import com.enonic.cms.core.content.index.queryexpression.FunctionExpr;
import com.enonic.cms.core.content.index.queryexpression.ValueExpr;

/**
 * Created by IntelliJ IDEA.
 * User: rmh
 * Date: 11/23/11
 * Time: 12:26 PM
 */
public final class IndexValueResolver
{
    private final static Function<String, String> lowerCaseFunction = new Function<String, String>()
    {
        @Override
        public String apply( String input )
        {
            return input.toLowerCase();
        }
    };

    public static Object[] toValues( Expression expr )
    {
        if ( expr instanceof ArrayExpr )
        {
            return toValues( (ArrayExpr) expr );
        }
        else if ( expr instanceof ValueExpr )
        {
            return new Object[]{toValue( (ValueExpr) expr )};
        }
        else if ( expr instanceof FunctionExpr )
        {
            return toValues( (FunctionExpr) expr );
        }
        else
        {
            return new String[0];
        }
    }

    public static Object toValue( ValueExpr expr )
    {
        if ( expr.isNumber() )
        {
            return expr.getValue();

        }
        else
        {
            final String stringValue = expr.getValue().toString();
            return StringUtils.lowerCase( stringValue );
        }
    }

    private static Object[] toValues( ArrayExpr expr )
    {
        final ValueExpr[] list = expr.getValues();
        final Object[] result = new Object[list.length];

        for ( int i = 0; i < list.length; i++ )
        {
            result[i] = toValue( list[i] );
        }

        return result;
    }

    private static Object[] toValues( FunctionExpr expr )
    {
        final FunctionEvaluator eval = new FunctionEvaluator();
        return toValues( (Expression) expr.evaluate( eval ) );
    }


}
